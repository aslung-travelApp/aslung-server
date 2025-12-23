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

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl; // GMS 전체 URL

    @Value("${openai.model}")
    private String modelName; // gpt-5-mini

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 메인 추천 로직
     */
    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        // 1. [RAG] 5만 개 공공데이터 중 키워드와 관련된 텍스트 검색
        String dbContext = searchDatabase(request.getKeyword());

        // 2. GPT에게 보낼 요청 메시지 조립
        String prompt = createPrompt(candidates, request, weather, dbContext);

        // 3. SSAFY GMS 서버로 직접 요청 (RestTemplate 사용)
        return callGMS(prompt, candidates);
    }

    // ✅ 5만 개 데이터도 0.1초 만에 찾아내는 SQL 검색
    private String searchDatabase(String keyword) {
        if (keyword == null || keyword.isEmpty()) return "관련 정보 없음";

        String sql = "SELECT name, address, overview FROM places WHERE name LIKE ? OR overview LIKE ? LIMIT 3";
        String param = "%" + keyword + "%";

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, param, param);
            if (rows.isEmpty()) return "관련 정보 없음";

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                sb.append(String.format("- 장소: %s | 설명: %s\n", row.get("name"), row.get("overview")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("DB 검색 중 에러: {}", e.getMessage());
            return "DB 검색 실패";
        }
    }

    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        return String.format("""
            당신은 한국 로컬 여행 가이드입니다.
            
            [현재 상황]
            - 날씨: %s
            - 스타일: %s
            - 사용자 키워드: %s
            
            [공공데이터 상세 정보 (우선 참고)]
            %s
            
            [카카오 주변 장소 후보]
            %s
            
            위 정보를 바탕으로 가장 적합한 장소의 ID와 추천 이유를 JSON으로만 답하세요.
            이유는 한국어로 친절하게 작성하세요.
            format: { "recommendations": [ { "id": "카카오ID", "reason": "이유" } ] }
            """, weather, req.getStyles(), req.getKeyword(), dbContext, candidates.toString());
    }

    private List<AiPlaceDto> callGMS(String prompt, List<AiPlaceDto> candidates) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "Respond in JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody(), candidates);
        } catch (Exception e) {
            log.error("GMS 호출 실패: {}", e.getMessage());
            return candidates.size() > 3 ? candidates.subList(0, 3) : candidates;
        }
    }

    private List<AiPlaceDto> parseResponse(String json, List<AiPlaceDto> candidates) {
        try {
            // 1. 전체 JSON을 Map으로 변환
            Map<String, Object> map = objectMapper.readValue(json, Map.class);

            // 2. "choices"를 꺼낼 때, List<Map>이라고 확실하게 명시!
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");

            // 3. 첫 번째 요소(get(0))를 가져와서, "message"를 꺼냄 (여기가 에러 났던 곳 해결!)
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            // 4. 최종적으로 content 꺼내기
            String content = (String) message.get("content");

            // --- 마크다운 제거 및 나머지 로직은 동일 ---
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            }

            // 3. 추천된 장소 ID를 기반으로 원본 정보(candidates) 찾아서 '이유(Reason)' 채워넣기
            List<AiPlaceDto> finalResult = new ArrayList<>();

            for (Map<String, String> rec : recommendations) {
                String recommendedId = rec.get("id");
                String recommendedReason = rec.get("reason");

                // 후보군(15개) 중에서 GPT가 픽한 장소 찾기
                candidates.stream()
                        .filter(place -> place.getId().equals(recommendedId))
                        .findFirst()
                        .ifPresent(place -> {
                            place.setReason(recommendedReason); // ★ 여기가 핵심! 이유 덮어쓰기
                            finalResult.add(place);
                        });
            }

            return finalResult;

        } catch (Exception e) {
            log.error("🚨 GPT 응답 파싱 실패 (형식이 안 맞음): {}", e.getMessage());
            // 파싱 실패 시, 비상용으로 그냥 앞에서 3개 잘라서 줌
            int limit = Math.min(candidates.size(), 3);
            return new ArrayList<>(candidates.subList(0, limit));
        }
    }
}