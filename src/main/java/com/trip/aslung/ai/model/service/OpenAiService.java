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
            // body.put("temperature", 0.7);

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

    public String generateChatResponse(String userMessage) {
        try {
            // 1. 요청 Body 생성
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName); // gpt-5-mini (설정 파일 값)

            // 메시지 구성 (System: 역할 부여 / User: 사용자 질문)
            List<Map<String, String>> messages = new ArrayList<>();

            // (1) 시스템 프롬프트: AI의 페르소나 설정
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a friendly and knowledgeable travel guide for Korea. Answer in Korean. Keep your answers concise and helpful.");
            messages.add(systemMessage);

            // (2) 사용자 메시지
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            body.put("messages", messages);
            // body.put("temperature", 0.7); // 창의성 조절 (필요시 주석 해제)

            // 2. Header 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey); // GMS API Key

            // 3. HTTP 요청 전송
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            // 4. 응답 파싱 (JSON -> String)
            // 응답 구조: choices[0].message.content
            Map map = objectMapper.readValue(response.getBody(), Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");

            return (String) message.get("content");

        } catch (Exception e) {
            log.error("AI 채팅 호출 실패: {}", e.getMessage());
            return "죄송해요, 지금은 AI가 너무 바빠서 대답할 수 없어요. 잠시 후 다시 시도해 주세요. 😥";
        }
    }

    // OpenAiService.java

    /**
     * [재추천] 사용자의 채팅 입력("카페만 보여줘")을 반영하여 다시 추천
     */
    public List<AiPlaceDto> refineRecommendations(String userPrompt) {
        log.info("AI 재추천 요청: {}", userPrompt);

        // 1. [RAG] 사용자 입력어(예: 카페, 조용한)로 DB에서 관련 장소 다시 검색
        String dbContext = searchDatabase(userPrompt);

        // 2. 후보군 조회 (DB에서 검색된 장소들을 후보군으로 변환)
        // (실제로는 DB 검색 결과인 Map을 AiPlaceDto로 변환하는 과정이 필요하지만,
        // 여기서는 간략히 searchDatabase 결과를 기반으로 가상의 후보군을 만든다고 가정하거나,
        // 혹은 전체 장소에서 다시 필터링한다고 가정합니다.)
        // ★ 편의상: DB 검색 결과에 나온 장소들을 후보군으로 사용
        List<AiPlaceDto> candidates = convertDbResultToDto(userPrompt);

        // 3. 프롬프트 생성 (사용자 요구사항 강조)
        String prompt = createRefinePrompt(userPrompt, dbContext, candidates);

        // 4. GMS 호출 및 결과 파싱
        return callGMS(prompt, candidates);
    }

    // (보조) 사용자 입력으로 DB를 뒤져서 후보군 DTO 리스트를 만드는 메서드
    // (보조) 사용자 입력으로 DB를 뒤져서 후보군 DTO 리스트를 만드는 메서드
    private List<AiPlaceDto> convertDbResultToDto(String keyword) {
        String sql = "SELECT place_id, name, address, content_type_id, overview, latitude, longitude FROM places WHERE name LIKE ? OR overview LIKE ? LIMIT 5";
        String param = "%" + keyword + "%";

        List<AiPlaceDto> list = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, param, param);
            for (Map<String, Object> row : rows) {
                AiPlaceDto dto = new AiPlaceDto();
                dto.setId(String.valueOf(row.get("place_id")));
                dto.setPlaceName((String) row.get("name"));
                dto.setAddress((String) row.get("address"));
                dto.setCategory(String.valueOf(row.get("content_type_id")));
                dto.setOverview((String) row.get("overview"));

                // [수정] NULL 체크를 추가하여 안전하게 변환
                if (row.get("latitude") != null) {
                    dto.setLat(Double.parseDouble(String.valueOf(row.get("latitude"))));
                }
                if (row.get("longitude") != null) {
                    dto.setLng(Double.parseDouble(String.valueOf(row.get("longitude"))));
                }

                // (선택 사항) GPT 호출 실패 시에도 기본 멘트가 나오도록 설정
                dto.setReason("키워드 '" + keyword + "' 관련 장소입니다.");

                list.add(dto);
            }
        } catch (Exception e) {
            log.error("DB 재검색 실패: {}", e.getMessage());
            // 에러 나도 빈 리스트 반환하여 서버가 죽지 않게 함
        }
        return list;
    }

    // (보조) 재추천용 프롬프트 생성
    private String createRefinePrompt(String userPrompt, String dbContext, List<AiPlaceDto> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("The user wants to refine the recommendations based on this request: \"").append(userPrompt).append("\"\n");
        sb.append("Select the best places from the list below that match the request.\n\n");

        sb.append("### [Candidate Places] ###\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- ID: %s | Name: %s | Overview: %s\n", p.getId(), p.getPlaceName(), p.getOverview()));
        }

        sb.append("\nOutput format: JSON with 'recommendations' list containing 'id' and a Korean 'reason'.");
        return sb.toString();
    }
}