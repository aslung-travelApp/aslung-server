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

        // 1. [날씨] 현재 위치(y:위도, x:경도) 날씨 조회
        String weatherInfo = "Clear";
        if (request.getX() != null && request.getY() != null) {
            // WeatherService는 (latStr, lngStr) 순서로 받으므로 (y, x) 전달
            weatherInfo = weatherService.getCurrentWeather(request.getY(), request.getX());
            log.info("현재 날씨: {}", weatherInfo);
        }

        // 2. [확장] 사용자 요청 -> 구체적 키워드 리스트 (GPT)
        List<String> keywords = expandToKeywords(userPrompt);
        log.info("확장된 키워드: {}", keywords);

        // 3. [수집] DB + Kakao 후보군 통합
        List<AiPlaceDto> combinedCandidates = new ArrayList<>();

        // (3-1) DB 검색
        List<AiPlaceDto> dbResults = searchPlacesByKeywords(keywords);
        combinedCandidates.addAll(dbResults);
        log.info("DB 검색 결과: {}건", dbResults.size());

        // (3-2) Kakao API 검색 (반경 5km)
        if (request.getX() != null && request.getY() != null) {
            for (String kw : keywords) {
                // 키워드별 검색 수행 (반경 5km = 5000m)
                List<AiPlaceDto> kakaoResults = kakaoService.searchPlacesByKeyword(kw, request.getX(), request.getY(), 5000);

                // Kakao 결과는 좌표가 String이므로 Double(lat, lng)로 변환해줘야 지도에 찍힘
                for (AiPlaceDto kPlace : kakaoResults) {
                    if (kPlace.getY() != null) kPlace.setLat(Double.parseDouble(kPlace.getY()));
                    if (kPlace.getX() != null) kPlace.setLng(Double.parseDouble(kPlace.getX()));
                    kPlace.setOverview("카카오맵 평점과 리뷰가 좋은 장소입니다."); // 기본 설명 추가
                }
                combinedCandidates.addAll(kakaoResults);
            }
        }

        // (3-3) 중복 제거 (ID 기준)
        combinedCandidates = removeDuplicates(combinedCandidates);
        log.info("통합 후보군 개수: {}건", combinedCandidates.size());

        // 4. [비상 대책] 후보군이 부족하면(3개 미만) -> 광범위 재검색 (Fallback)
        if (combinedCandidates.size() < 3 && request.getX() != null) {
            log.info("후보군 부족! Fallback 검색 실행...");
            List<String> fallbackKeywords = new ArrayList<>();
            // 사용자의 취향(styles)이 있다면 그걸로 검색, 없으면 기본 키워드
            if (request.getStyles() != null && !request.getStyles().isEmpty()) {
                // "힐링", "액티비티" 같은 스타일 리스트를 키워드로 사용
                fallbackKeywords.addAll(request.getStyles()); // 수정: List<String>을 바로 addAll
            } else {
                fallbackKeywords.add("관광명소");
                fallbackKeywords.add("맛집");
                fallbackKeywords.add("카페");
            }

            for (String fbKw : fallbackKeywords) {
                List<AiPlaceDto> fbResults = kakaoService.searchPlacesByKeyword(fbKw, request.getX(), request.getY(), 10000); // 반경 10km
                for (AiPlaceDto kPlace : fbResults) {
                    if (kPlace.getY() != null) kPlace.setLat(Double.parseDouble(kPlace.getY()));
                    if (kPlace.getX() != null) kPlace.setLng(Double.parseDouble(kPlace.getX()));
                }
                combinedCandidates.addAll(fbResults);
            }
            combinedCandidates = removeDuplicates(combinedCandidates);
        }

        // 그래도 없으면 빈 리스트
        if (combinedCandidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 5. [선택] GPT에게 최종 3곳 선정 요청 (날씨 정보 포함)
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