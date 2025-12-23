package com.trip.aslung.ai.model.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.aslung.ai.model.dto.AiPlaceDto;
import com.trip.aslung.ai.model.dto.AiRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    // application.properties 설정값 주입
    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl; // https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions

    @Value("${openai.model}")
    private String modelName; // gpt-5-mini

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * [메인 메서드] AI 추천 실행
     * 1. DB 검색 (RAG)
     * 2. 프롬프트 생성
     * 3. GPT 호출 및 결과 파싱
     */
    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        // 요청 데이터가 제대로 들어왔는지 로그 확인
        log.info("=== AI 추천 요청 데이터 ===");
        log.info("날씨: {}", weather);
        log.info("동행자: {}", request.getCompanion());
        log.info("스타일: {}", request.getStyles());
        log.info("키워드: {}", request.getKeyword());
        log.info("후보군 개수: {}", candidates.size());
        // 1. [RAG] 5만 개 데이터 중 키워드와 관련된 내용 찾기 (SQL LIKE)
        String dbContext = searchDatabase(request.getKeyword());

        // 2. 프롬프트 조립 (날씨 + 사용자정보 + DB정보 + 카카오후보군)
        String prompt = createPrompt(candidates, request, weather, dbContext);

        // 3. SSAFY GMS 서버로 전송
        return callGMS(prompt, candidates);
    }

    // ✅ 1단계: DB 검색 (Spring AI 대신 SQL 사용 -> 속도 빠름)
    private String searchDatabase(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return "특별히 지정된 키워드 정보 없음.";
        }

        // 이름이나 설명에 키워드가 포함된 장소 상위 3개만 조회
        String sql = "SELECT name, address, overview FROM places WHERE name LIKE ? OR overview LIKE ? LIMIT 3";
        String param = "%" + keyword + "%";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, param, param);

            if (rows.isEmpty()) return "관련된 DB 정보 없음.";

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                sb.append(String.format("- 장소명: %s | 주소: %s | 설명: %s\n",
                        row.get("name"), row.get("address"), row.get("overview")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("DB 검색 중 에러 발생: {}", e.getMessage());
            return "DB 검색 실패 (GPT가 자체 지식으로 판단합니다)";
        }
    }

    // ✅ 2단계: 프롬프트 생성 (English Version)
    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        StringBuilder sb = new StringBuilder();

        // 상황 정보 (Travel Context)
        sb.append("### [Travel Context] ###\n");
        sb.append("- Weather: ").append(weather).append("\n");
        sb.append("- Companion: ").append(req.getCompanion()).append("\n");
        sb.append("- Travel Style: ").append(req.getStyles()).append("\n");
        sb.append("- Interest Keyword: ").append(req.getKeyword()).append("\n\n");

        // RAG 정보 (Public Data Context)
        sb.append("### [Key Public Data Context (Priority Reference)] ###\n");
        sb.append(dbContext).append("\n\n");

        // 후보군 정보 (Candidate Places)
        sb.append("### [Nearby Candidate Places] ###\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- ID: %s | Name: %s | Category: %s\n",
                    p.getId(), p.getPlaceName(), p.getCategory()));
        }

        // 지시사항 (Instructions)
        sb.append("\n### [Instructions] ###\n");
        sb.append("You are a professional travel guide. Select the 3 places that best match the user's [Travel Context] from the [Nearby Candidate Places] list above.\n");

        // 중요 조건
        sb.append("- **IMPORTANT**: You MUST consider the [Travel Style] and [Companion] type when making your selection.\n");

        // 상세 단계
        sb.append("1. Analyze and compare the 'Key Public Data Context' with the 'Nearby Candidate Places'.\n");
        sb.append("2. Select places that best fit the current weather and style.\n");
        sb.append("- Example: If the weather is 'Rain', recommend indoor activities.\n");

        // ** 핵심: 출력 언어 지정 **
        sb.append("3. For each selected place, write a specific 'reason' explaining **why this place fits the user's style and weather**.\n");
        sb.append("   - **NOTE: The 'reason' value MUST be written in KOREAN.**\n");

        // JSON 제약 조건
        sb.append("4. CRITICAL: The JSON key for the explanation MUST be named 'reason'. Do NOT use 'description' or 'content'.\n");
        sb.append("5. The output must be strictly in the following JSON format only. (Do NOT use Markdown blocks like ```json).\n");
        sb.append("Format: { \"recommendations\": [ { \"id\": \"(Place ID)\", \"reason\": \"(Reason in Korean, 2~3 sentences)\" } ] }");

        return sb.toString();
    }

    // ✅ 3단계: GMS 호출 및 파싱 (RestTemplate 사용)
    private List<AiPlaceDto> callGMS(String prompt, List<AiPlaceDto> candidates) {
        try {
            // 요청 Body 생성
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName); // gpt-5-mini
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a helpful travel guide. Respond in JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));
            body.put("temperature", 0.7);

            // Header 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey); // GMS Key

            // HTTP 요청 전송
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            // 응답 파싱
            return parseResponse(response.getBody(), candidates);

        } catch (Exception e) {
            log.error("GPT 호출 실패: {}", e.getMessage());
            // 실패 시 안전하게 후보군 중 3개만 리턴
            int limit = Math.min(candidates.size(), 3);
            return new ArrayList<>(candidates.subList(0, limit));
        }
    }

    // JSON 응답 해석기
    private List<AiPlaceDto> parseResponse(String jsonResponse, List<AiPlaceDto> candidates) {
        try {
            Map map = objectMapper.readValue(jsonResponse, Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            String content = (String) message.get("content");

            // 가끔 GPT가 ```json ... ``` 을 붙여서 줄 때가 있어서 제거함
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            }

            Map contentMap = objectMapper.readValue(content, Map.class);
            List<Map<String, String>> recs = (List<Map<String, String>>) contentMap.get("recommendations");

            List<AiPlaceDto> result = new ArrayList<>();
            for (Map<String, String> r : recs) {
                String id = r.get("id");
                String reason = r.get("reason");

                log.info("GPT 응답 - ID: {}, Reason: {}", id, reason);
                
                // 후보군 리스트에서 ID가 같은 녀석을 찾아서 '이유'를 덮어씀
                candidates.stream()
                        .filter(c -> c.getId().equals(id))
                        .findFirst()
                        .ifPresent(place -> {
                            place.setReason(reason);
                            result.add(place);
                        });
            }
            return result;

        } catch (Exception e) {
            log.error("JSON 파싱 에러: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}