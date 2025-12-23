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
        sb.append("1. '공공데이터 핵심 정보'와 '주변 후보 장소'를 비교 분석하세요.\n");
        sb.append("2. 공공데이터에 있는 장소가 후보 목록에도 있다면, 해당 장소를 강력 추천하세요.\n");
        sb.append("3. 날씨와 스타일에 가장 잘 어울리는 장소를 선택하세요.\n");
        sb.append("4. 결과는 반드시 아래 JSON 형식으로만 출력하세요. (Markdown 사용 금지)\n");
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

    // JSON 응답 해석기(수정)
    private List<AiPlaceDto> parseResponse(String jsonResponse, List<AiPlaceDto> candidates) {
        try {
            // ★ 1. 전체 응답 로그 찍어보기 (서버 콘솔 확인용)
            log.info("### GPT Raw Response: {}", jsonResponse);

            Map map = objectMapper.readValue(jsonResponse, Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            String content = (String) message.get("content");

            // 마크다운 제거
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            }

            // ★ 2. 정제된 content 내용 확인
            log.info("### Parsed Content: {}", content);

            Map contentMap = objectMapper.readValue(content, Map.class);
            List<Map<String, String>> recs = (List<Map<String, String>>) contentMap.get("recommendations");

            List<AiPlaceDto> result = new ArrayList<>();
            for (Map<String, String> r : recs) {
                String id = String.valueOf(r.get("id")); // 숫자로 올 수도 있으니 문자열로 변환

                // ★ 3. GPT가 'reason' 말고 딴소리 할 경우 대비 (키 값 찾기)
                String reason = r.get("reason");
                if (reason == null) reason = r.get("description");
                if (reason == null) reason = r.get("desc");
                if (reason == null) reason = r.get("detail");
                if (reason == null) reason = "AI 추천 사유를 불러오지 못했습니다.";

                String finalReason = reason; // 람다식용 final 변수

                candidates.stream()
                        .filter(c -> c.getId().equals(id))
                        .findFirst()
                        .ifPresent(place -> {
                            place.setReason(finalReason);
                            result.add(place);
                        });
            }
            return result;

        } catch (Exception e) {
            log.error("JSON 파싱 에러: {}", e.getMessage());
            e.printStackTrace(); // 에러 상세 내용 출력
            return new ArrayList<>();
        }
    }
}