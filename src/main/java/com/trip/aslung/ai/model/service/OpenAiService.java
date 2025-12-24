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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String modelName;

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 외부 서비스 주입
    private final WeatherService weatherService;
    private final KakaoService kakaoService;

    // =================================================================================
    // 1. 메인 추천 (기존 로직 유지)
    // =================================================================================
    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        log.info("=== AI 추천 요청 ===");
        String dbContext = searchDatabase(request.getKeyword());
        String prompt = createPrompt(candidates, request, weather, dbContext);
        return callGMS(prompt, candidates);
    }

    private String searchDatabase(String keyword) {
        if (keyword == null || keyword.isEmpty()) return "특별히 지정된 키워드 정보 없음.";
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
            return "DB 검색 실패";
        }
    }

    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("### [Travel Context] ###\n");
        sb.append("- Weather: ").append(weather).append("\n");
        sb.append("- Companion: ").append(req.getCompanion()).append("\n");
        sb.append("- Travel Style: ").append(req.getStyles()).append("\n");
        sb.append("- Interest Keyword: ").append(req.getKeyword()).append("\n\n");
        sb.append("### [Key Public Data Context] ###\n").append(dbContext).append("\n\n");
        sb.append("### [Nearby Candidate Places] ###\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- ID: %s | Name: %s | Category: %s\n", p.getId(), p.getPlaceName(), p.getCategory()));
        }
        sb.append("\n### [Instructions] ###\n");
        sb.append("Select 3 best places matching the context. Explain the 'reason' in Korean.\n");
        sb.append("Format: { \"recommendations\": [ { \"id\": \"...\", \"reason\": \"...\" } ] }");
        return sb.toString();
    }

    // =================================================================================
    // 2. 공통 GMS 호출 및 파싱 (유지)
    // =================================================================================
    private List<AiPlaceDto> callGMS(String prompt, List<AiPlaceDto> candidates) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a helpful travel guide. Respond in JSON only."),
                    Map.of("role", "user", "content", prompt)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            return parseResponse(response.getBody(), candidates);
        } catch (Exception e) {
            log.error("GPT 호출 실패: {}", e.getMessage());
            int limit = Math.min(candidates.size(), 3);
            return new ArrayList<>(candidates.subList(0, limit));
        }
    }

    private List<AiPlaceDto> parseResponse(String jsonResponse, List<AiPlaceDto> candidates) {
        try {
            Map map = objectMapper.readValue(jsonResponse, Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            String content = (String) message.get("content");
            if (content.contains("```json")) content = content.replace("```json", "").replace("```", "");

            Map contentMap = objectMapper.readValue(content, Map.class);
            List<Map<String, String>> recs = (List<Map<String, String>>) contentMap.get("recommendations");

            List<AiPlaceDto> result = new ArrayList<>();
            for (Map<String, String> r : recs) {
                String id = r.get("id");
                String reason = r.get("reason");
                candidates.stream().filter(c -> c.getId().equals(id)).findFirst().ifPresent(place -> {
                    place.setReason(reason);
                    result.add(place);
                });
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // =================================================================================
    // 3. 단순 채팅 (유지)
    // =================================================================================
    public String generateChatResponse(String userMessage) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a friendly travel guide for Korea. Answer in Korean."),
                    Map.of("role", "user", "content", userMessage)
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            Map map = objectMapper.readValue(response.getBody(), Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "죄송해요, 잠시 후 다시 시도해 주세요.";
        }
    }

    // =================================================================================
    // ★ 4. Logic RAG + Hybrid Retrieval (DB + Kakao) + Weather + Fallback
    // =================================================================================

    /**
     * [재추천] 사용자 입력 + 위치 정보 + 날씨 기반 통합 추천
     */
    public List<AiPlaceDto> refineRecommendations(AiRequestDto request) {
        String userPrompt = request.getMessage();
        log.info("AI 재추천 시작 - 요청: {}, 위치: x={}, y={}", userPrompt, request.getX(), request.getY());

        // 1. [날씨]
        String weatherInfo = "Clear";
        if (request.getX() != null && request.getY() != null) {
            weatherInfo = weatherService.getCurrentWeather(request.getY(), request.getX());
        }

        // 2. [확장] "뜨끈한 국물" -> ["국밥", "전골", "이자카야", "오뎅바"]
        List<String> keywords = expandToKeywords(userPrompt);
        log.info("확장된 키워드: {}", keywords);

        // 3. [1차 수집] DB + Kakao (반경 5km)
        List<AiPlaceDto> combinedCandidates = new ArrayList<>();

        // (3-1) DB 검색
        combinedCandidates.addAll(searchPlacesByKeywords(keywords));

        // (3-2) Kakao API 검색 (5km)
        if (request.getX() != null && request.getY() != null) {
            for (String kw : keywords) {
                combinedCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, request.getX(), request.getY(), 5000));
            }
        }

        combinedCandidates = removeDuplicates(combinedCandidates);
        log.info("1차 검색 결과: {}건", combinedCandidates.size());

        // =========================================================================
        // ★ [수정됨] 4. [비상 대책] 반경을 20km로 넓혀서 '원래 키워드'만 다시 검색
        // (맛집, 카페 같은 엉뚱한 기본값 추가 로직 삭제함)
        // =========================================================================
        if (combinedCandidates.size() < 3 && request.getX() != null) {
            log.info("결과 부족! 반경을 20km로 넓혀서 재검색합니다.");

            for (String kw : keywords) {
                List<AiPlaceDto> wideResults = kakaoService.searchPlacesByKeyword(kw, request.getX(), request.getY(), 20000);
                combinedCandidates.addAll(wideResults);
            }

            // (옵션) 사용자가 회원가입 때 설정한 '취향 태그(styles)'가 있다면 그것까지는 봐줌 (문맥상 관련 있을 확률 높음)
            if (request.getStyles() != null && !request.getStyles().isEmpty()) {
                for (String style : request.getStyles()) {
                    combinedCandidates.addAll(kakaoService.searchPlacesByKeyword(style, request.getX(), request.getY(), 20000));
                }
            }

            combinedCandidates = removeDuplicates(combinedCandidates);
        }

        // 5. [최종 확인] 그래도 없으면 깔끔하게 빈 리스트 리턴 (엉뚱한 추천 방지)
        if (combinedCandidates.isEmpty()) {
            log.warn("20km 반경 내에서도 키워드 관련 장소를 찾지 못함.");
            return new ArrayList<>();
        }

        // 6. [선택] GPT 선정
        String prompt = createPrompt(combinedCandidates, request, weatherInfo, "Focus on the user request: " + userPrompt);
        return callGMS(prompt, combinedCandidates);
    }

    // (보조) 중복 제거
    private List<AiPlaceDto> removeDuplicates(List<AiPlaceDto> list) {
        return list.stream()
                .filter(distinctByKey(AiPlaceDto::getId))
                .collect(Collectors.toList());
    }

    private static <T> java.util.function.Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        Set<Object> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    // (4-1) GPT 키워드 확장
    private List<String> expandToKeywords(String userPrompt) {
        if (userPrompt == null || userPrompt.length() < 2) return List.of(userPrompt);

        try {
            String prompt = "Analyze the user's abstract travel request and convert it into 3~5 concrete search keywords(nouns) to find places in a database or map.\n" +
                    "User Request: \"" + userPrompt + "\"\n" +
                    "Examples:\n" +
                    "- 'It's too cold' -> '카페, 미술관, 박물관, 쇼핑몰, 실내'\n" +
                    "- 'Quiet place' -> '도서관, 공원, 산책로, 사찰, 숲'\n" +
                    "Output ONLY the keywords separated by comma(,), in Korean. Do not add any explanation.";

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a query expander. Output only comma-separated keywords."),
                    Map.of("role", "user", "content", prompt)
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            Map map = objectMapper.readValue(response.getBody(), Map.class);
            List choices = (List) map.get("choices");
            Map message = (Map) ((Map) choices.get(0)).get("message");
            String content = (String) message.get("content");

            String[] keywords = content.split(",");
            List<String> result = new ArrayList<>();
            for (String k : keywords) {
                result.add(k.trim().replace(".", ""));
            }
            return result;
        } catch (Exception e) {
            log.error("키워드 확장 실패: {}", e.getMessage());
            return List.of(userPrompt);
        }
    }

    // (4-2) DB 검색 (Dynamic SQL)
    private List<AiPlaceDto> searchPlacesByKeywords(List<String> keywords) {
        if (keywords.isEmpty()) return new ArrayList<>();

        StringBuilder sql = new StringBuilder("SELECT place_id, name, address, content_type_id, overview, latitude, longitude FROM places WHERE ");
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("(name LIKE ? OR overview LIKE ?)");
            params.add("%" + keywords.get(i) + "%");
            params.add("%" + keywords.get(i) + "%");
        }
        sql.append(" LIMIT 5");

        List<AiPlaceDto> list = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            for (Map<String, Object> row : rows) {
                AiPlaceDto dto = new AiPlaceDto();
                dto.setId(String.valueOf(row.get("place_id")));
                dto.setPlaceName((String) row.get("name"));
                dto.setAddress((String) row.get("address"));
                dto.setCategory(String.valueOf(row.get("content_type_id")));
                dto.setOverview((String) row.get("overview"));

                if (row.get("latitude") != null) dto.setLat(Double.parseDouble(String.valueOf(row.get("latitude"))));
                if (row.get("longitude") != null) dto.setLng(Double.parseDouble(String.valueOf(row.get("longitude"))));

                dto.setReason("사용자의 요청을 분석하여 찾은 추천 장소입니다.");
                list.add(dto);
            }
        } catch (Exception e) {
            log.error("DB 확장 검색 실패: {}", e.getMessage());
        }
        return list;
    }
}