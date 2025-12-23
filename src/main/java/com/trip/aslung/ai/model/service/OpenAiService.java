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

    // ✅ 2단계: 프롬프트 생성
    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        StringBuilder sb = new StringBuilder();

        // 상황 정보
        sb.append("### [여행 상황] ###\n");
        sb.append("- 날씨: ").append(weather).append("\n");
        sb.append("- 동행자: ").append(req.getCompanion()).append("\n");
        sb.append("- 스타일: ").append(req.getStyles()).append("\n");
        sb.append("- 관심 키워드: ").append(req.getKeyword()).append("\n\n");

        // RAG 정보 (우선순위 높음)
        sb.append("### [공공데이터 핵심 정보 (우선 참고)] ###\n");
        sb.append(dbContext).append("\n\n");

        // 후보군 정보 (카카오)
        sb.append("### [주변 후보 장소 목록] ###\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- ID: %s | 이름: %s | 카테고리: %s\n",
                    p.getId(), p.getPlaceName(), p.getCategory()));
        }

        // 지시사항
        sb.append("\n### [지시사항] ###\n");
        sb.append("당신은 전문 여행 가이드입니다. 위 [주변 후보 장소 목록] 중에서 사용자의 [여행 조건]에  가장 완벽하게 부합하는 장소 3곳을 엄선하세요.\n");
        sb.append("- **중요**: [선호 여행 스타일]과 [동행자 유형]을 반드시 고려하여 선택해야 합니다.\n");
        sb.append("1. '공공데이터 핵심 정보'와 '주변 후보 장소'를 비교 분석하세요.\n");
        sb.append("2. 날씨와 스타일에 가장 잘 어울리는 장소를 선택하세요.\n");
        sb.append("- 예: 날씨가 '비'라면 실내 위주로 추천하세요.\n");
        sb.append("3. 선택한 장소에 대해 **왜 이 장소가 사용자의 스타일과 날씨에 딱 맞는지** 구체적인 이유(reason)를 '한국어'로 작성하세요.\n");
        sb.append("4. CRITICAL: The key MUST be named 'reason'. Do NOT use 'description' or 'content'.\n");
        sb.append("5. 결과는 반드시 아래 JSON 형식으로만 출력하세요. (Markdown 사용 금지)\n");
        sb.append("형식: { \"recommendations\": [ { \"id\": \"(후보장소ID)\", \"reason\": \"(추천이유 - 한국어 2~3문장)\" } ] }");

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