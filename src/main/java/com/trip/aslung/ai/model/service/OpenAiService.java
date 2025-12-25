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
    private final WeatherService weatherService;
    private final KakaoService kakaoService;

    // =================================================================================
    // ★ 1. 메인 추천 (처음 들어왔을 때)
    // =================================================================================
    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        log.info("=== AI 초기 추천 요청 ===");
        log.info("입력 키워드: {}, 위치: {}, {}", request.getKeyword(), request.getX(), request.getY());

        // ▼▼▼ [핵심 수정] 초기 후보군이 없으면(0개), 좌표를 이용해 카카오에서 즉시 검색해온다! ▼▼▼
        if (candidates == null || candidates.isEmpty()) {
            log.info("🚨 초기 후보군 없음! 좌표 기반 스마트 검색 시작...");
            // 사용자의 키워드와 좌표로 검색 수행
            candidates = fetchCandidatesSmartly(request.getKeyword(), request.getX(), request.getY());
        }

        // 그래도 없으면 빈 리스트 반환
        if (candidates.isEmpty()) {
            log.warn("초기 검색 결과 0건. (위치 정보 확인 필요)");
            return new ArrayList<>();
        }

        // 후보군이 너무 많으면 상위 15개만 사용 (비용 절약)
        if (candidates.size() > 15) {
            candidates = new ArrayList<>(candidates.subList(0, 15));
        }

        // 초기 추천은 DB Context도 참고 (없으면 무시)
        String dbContext = searchDatabase(request.getKeyword());
        String prompt = createPrompt(candidates, request, weather, dbContext);

        return callGMS(prompt, candidates);
    }

    // =================================================================================
    // ★ 2. 재추천 (채팅 입력 시)
    // =================================================================================
    public List<AiPlaceDto> refineRecommendations(AiRequestDto request) {
        String userPrompt = request.getMessage();
        log.info("🚀 AI 실시간 재추천: \"{}\" (위치: {}, {})", userPrompt, request.getX(), request.getY());

        // 1. 날씨 조회
        String weatherInfo = "정보 없음";
        if (request.getX() != null && request.getY() != null) {
            try {
                weatherInfo = weatherService.getCurrentWeather(request.getY(), request.getX());
            } catch (Exception e) {}
        }

        // 2. 스마트 검색 수행 (키워드 확장 -> 카카오/DB 검색)
        List<AiPlaceDto> candidates = fetchCandidatesSmartly(userPrompt, request.getX(), request.getY());

        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. GPT 선정
        int limit = Math.min(candidates.size(), 15);
        List<AiPlaceDto> topCandidates = new ArrayList<>(candidates.subList(0, limit));
        String prompt = createRefinePrompt(topCandidates, userPrompt, weatherInfo);

        return callGMS(prompt, topCandidates);
    }

    // =================================================================================
    // ★ 3. [공통 로직] 스마트 후보군 수집 (위치 있으면 주변, 없으면 전국)
    // =================================================================================
    private List<AiPlaceDto> fetchCandidatesSmartly(String userInput, String x, String y) {
        List<AiPlaceDto> combinedCandidates = new ArrayList<>();

        // (1) 키워드 확장 ("국밥" -> "순대국, 돼지국밥, 해장국")
        List<String> keywords = expandToKeywords(userInput);
        log.info("🔍 확장된 검색 키워드: {}", keywords);

        // (2) DB 검색 (위치 무관하게 검색 가능)
        combinedCandidates.addAll(searchPlacesByKeywords(keywords));

        // (3) 카카오 검색
        // KakaoService가 null 체크를 하므로 안심하고 호출
        for (String kw : keywords) {
            // 1차: 5km 반경
            combinedCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, x, y, 5000));
        }

        combinedCandidates = removeDuplicates(combinedCandidates);

        // 결과가 부족하고, 위치 정보가 확실히 있다면 20km로 확장
        if (combinedCandidates.size() < 3 && x != null && y != null) {
            log.info("⚠️ 결과 부족. 20km 반경으로 확장 검색...");
            for (String kw : keywords) {
                combinedCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, x, y, 20000));
            }
        }
        // 위치 정보가 아예 없어서 결과가 0개인 경우 -> 전국 단위 검색 시도
        else if (combinedCandidates.isEmpty()) {
            log.info("⚠️ 위치 정보 없음. 전국 단위 검색 시도...");
            for (String kw : keywords) {
                combinedCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, null, null, 0));
            }
        }

        return removeDuplicates(combinedCandidates);
    }

    // =================================================================================
    // 4. 보조 메서드들
    // =================================================================================

    // (GPT 키워드 확장)
    private List<String> expandToKeywords(String userPrompt) {
        if (userPrompt == null || userPrompt.length() < 2) return List.of(userPrompt);
        try {
            String prompt = "Convert user request to 3~4 Korean search keywords(nouns) for map search.\n" +
                    "Example: 'hot soup' -> '국밥, 찌개, 전골, 우동'\n" +
                    "User Request: \"" + userPrompt + "\"\n" +
                    "Output ONLY keywords separated by comma(,)";

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(Map.of("role", "system", "content", "Keyword generator."), Map.of("role", "user", "content", prompt)));
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
            for (String k : keywords) result.add(k.trim().replace(".", ""));
            return result;
        } catch (Exception e) { return List.of(userPrompt); }
    }

    // (DB 검색)
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
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<AiPlaceDto> list = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                AiPlaceDto dto = new AiPlaceDto();
                dto.setId(String.valueOf(row.get("place_id")));
                dto.setPlaceName((String) row.get("name"));
                dto.setAddress((String) row.get("address"));
                dto.setCategory(String.valueOf(row.get("content_type_id")));
                dto.setOverview((String) row.get("overview"));
                if (row.get("latitude") != null) dto.setLat(Double.parseDouble(String.valueOf(row.get("latitude"))));
                if (row.get("longitude") != null) dto.setLng(Double.parseDouble(String.valueOf(row.get("longitude"))));
                dto.setReason("AI DB 추천");
                list.add(dto);
            }
            return list;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // (중복 제거)
    private List<AiPlaceDto> removeDuplicates(List<AiPlaceDto> list) {
        return list.stream().filter(distinctByKey(AiPlaceDto::getId)).collect(Collectors.toList());
    }
    private static <T> java.util.function.Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        Set<Object> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    // (프롬프트 생성)
    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context: Weather=").append(weather).append(", Keyword=").append(req.getKeyword()).append("\n");
        sb.append("Candidates:\n");
        for (AiPlaceDto p : candidates) sb.append(String.format("- ID:%s, Name:%s\n", p.getId(), p.getPlaceName()));
        sb.append("Select 3 best places. Return JSON with Korean 'reason'.");
        return sb.toString();
    }

    // (재추천 프롬프트)
    private String createRefinePrompt(List<AiPlaceDto> candidates, String userRequest, String weather) {
        StringBuilder sb = new StringBuilder();
        sb.append("Weather: ").append(weather).append("\nUser Request: ").append(userRequest).append("\n");
        sb.append("Candidates:\n");
        for (AiPlaceDto p : candidates) sb.append(String.format("- ID:%s, Name:%s, Category:%s\n", p.getId(), p.getPlaceName(), p.getCategory()));
        sb.append("Select 3 places matching request. Return JSON with Korean 'reason'.");
        return sb.toString();
    }

    // (DB 단순 검색)
    private String searchDatabase(String keyword) {
        if(keyword == null) return "";
        return "";
    }

    // (GMS 호출)
    private List<AiPlaceDto> callGMS(String prompt, List<AiPlaceDto> candidates) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(Map.of("role", "system", "content", "Respond in JSON only."), Map.of("role", "user", "content", prompt)));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            return parseResponse(response.getBody(), candidates);
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // (JSON 파싱)
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
                candidates.stream().filter(c -> c.getId().equals(r.get("id"))).findFirst().ifPresent(p -> {
                    p.setReason(r.get("reason"));
                    result.add(p);
                });
            }
            return result;
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // 단순 채팅
    public String generateChatResponse(String userMessage) { return "잠시만요"; }
}