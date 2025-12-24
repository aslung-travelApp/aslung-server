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
    // 1. 메인 추천 (처음 들어왔을 때 - DB 활용 유지)
    // =================================================================================
    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        log.info("=== AI 초기 추천 요청 ===");
        String dbContext = searchDatabase(request.getKeyword());
        String prompt = createPrompt(candidates, request, weather, dbContext);
        return callGMS(prompt, candidates);
    }

    // (보조 메서드들은 맨 아래에 몰아두겠습니다)

    // =================================================================================
    // ★ 2. [재추천] 후보군 무시! 오직 카카오맵 + 날씨 + GPT로 승부
    // =================================================================================
    public List<AiPlaceDto> refineRecommendations(AiRequestDto request) {
        String userPrompt = request.getMessage();
        log.info("🚀 AI 실시간 재추천 시작: \"{}\" (위치: {}, {})", userPrompt, request.getX(), request.getY());

        // 1. [날씨] 실시간 날씨 확인
        String weatherInfo = "정보 없음";
        if (request.getX() != null && request.getY() != null) {
            try {
                weatherInfo = weatherService.getCurrentWeather(request.getY(), request.getX());
                log.info("🌦️ 현재 날씨: {}", weatherInfo);
            } catch (Exception e) {
                log.warn("날씨 조회 실패");
            }
        }

        // 2. [키워드 확장] "뜨끈한 국물" -> ["국밥", "전골", "우동", "찌개"]
        List<String> keywords = expandToKeywords(userPrompt);
        log.info("🔍 검색할 키워드: {}", keywords);

        // 3. [카카오 검색] DB 무시하고 외부 데이터(Kakao) 수집
        List<AiPlaceDto> rawCandidates = new ArrayList<>();

        if (request.getX() != null && request.getY() != null) {
            // (3-1) 1차 시도: 반경 5km 검색
            for (String kw : keywords) {
                rawCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, request.getX(), request.getY(), 5000));
            }
            rawCandidates = removeDuplicates(rawCandidates);

            // (3-2) 2차 시도: 결과가 3개 미만이면 반경 20km로 확장
            if (rawCandidates.size() < 3) {
                log.info("⚠️ 5km 내 결과 부족({}개). 20km로 확장 검색...", rawCandidates.size());
                for (String kw : keywords) {
                    // 이미 찾은 건 중복제거되니 안심하고 다시 검색
                    rawCandidates.addAll(kakaoService.searchPlacesByKeyword(kw, request.getX(), request.getY(), 20000));
                }
                rawCandidates = removeDuplicates(rawCandidates);
            }
        } else {
            log.error("❌ 사용자 위치 정보(X,Y)가 없습니다. 재추천 불가.");
            return new ArrayList<>();
        }

        log.info("📦 수집된 후보 장소: {}개", rawCandidates.size());

        if (rawCandidates.isEmpty()) {
            return new ArrayList<>(); // 정말 없는 경우 빈 리스트 반환
        }

        // 4. [GPT 선정] 날씨와 사용자 요청에 맞춰서 최종 3곳 선정
        // 후보군이 너무 많으면 GPT 비용이 비싸지니 상위 15개만 자름
        int limit = Math.min(rawCandidates.size(), 15);
        List<AiPlaceDto> topCandidates = new ArrayList<>(rawCandidates.subList(0, limit));

        // 프롬프트에 "날씨"와 "사용자 요청"을 강력하게 주입
        String prompt = createRefinePrompt(topCandidates, userPrompt, weatherInfo);

        return callGMS(prompt, topCandidates);
    }

    // =================================================================================
    // 3. 보조 메서드들
    // =================================================================================

    // (3-1) 재추천용 프롬프트 생성 (날씨 강조)
    private String createRefinePrompt(List<AiPlaceDto> candidates, String userRequest, String weather) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Weather: ").append(weather).append("\n");
        sb.append("User Request: \"").append(userRequest).append("\"\n\n");

        sb.append("### Candidate Places (from KakaoMap) ###\n");
        for (AiPlaceDto p : candidates) {
            // 카카오 데이터에는 overview가 없으므로 카테고리로 대체
            sb.append(String.format("- ID: %s | Name: %s | Category: %s\n",
                    p.getId(), p.getPlaceName(), p.getCategory()));
        }

        sb.append("\n### Instructions ###\n");
        sb.append("1. Select the 3 best places that perfectly match the 'User Request' and 'Current Weather'.\n");
        sb.append("2. If the user asked for food (e.g., soup), DO NOT recommend tourist spots unless they serve food.\n");
        sb.append("3. Explain the 'reason' in Korean, specifically mentioning why it fits the request.\n");
        sb.append("Format: { \"recommendations\": [ { \"id\": \"...\", \"reason\": \"...\" } ] }");

        return sb.toString();
    }

    // (3-2) 키워드 확장 (GPT)
    private List<String> expandToKeywords(String userPrompt) {
        if (userPrompt == null || userPrompt.length() < 2) return List.of(userPrompt);
        try {
            // 프롬프트: 사용자의 의도를 구체적인 '검색용 명사'로 변환
            String prompt = "Convert the user's request into 3~4 concrete Korean search keywords(nouns) for KakaoMap.\n" +
                    "Examples:\n" +
                    "- 'hot soup' -> '국밥, 찌개, 전골, 우동'\n" +
                    "- 'date spot' -> '카페, 레스토랑, 파스타, 칵테일바'\n" +
                    "User Request: \"" + userPrompt + "\"\n" +
                    "Output ONLY keywords separated by comma(,)";

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a keyword generator."),
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
            return List.of(userPrompt);
        }
    }

    // (3-3) 중복 제거
    private List<AiPlaceDto> removeDuplicates(List<AiPlaceDto> list) {
        return list.stream()
                .filter(distinctByKey(AiPlaceDto::getId))
                .collect(Collectors.toList());
    }

    private static <T> java.util.function.Predicate<T> distinctByKey(java.util.function.Function<? super T, ?> keyExtractor) {
        Set<Object> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    // (3-4) 기존 DB 검색 로직 (초기 추천용 - 유지)
    private String searchDatabase(String keyword) {
        if (keyword == null || keyword.isEmpty()) return "";
        String sql = "SELECT name, address, overview FROM places WHERE name LIKE ? OR overview LIKE ? LIMIT 3";
        String param = "%" + keyword + "%";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, param, param);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                sb.append(String.format("- %s (%s): %s\n", row.get("name"), row.get("address"), row.get("overview")));
            }
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // (3-5) 초기 추천용 프롬프트 생성 (유지)
    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather, String dbContext) {
        // ... (기존 createPrompt 코드 내용 그대로 유지) ...
        // (지면상 생략하지만, 기존에 쓰시던 코드 그대로 두시면 됩니다)
        StringBuilder sb = new StringBuilder();
        sb.append("User Request Context:\n");
        sb.append("- Weather: ").append(weather).append("\n");
        sb.append("- Keywords: ").append(req.getKeyword()).append("\n");
        sb.append("- DB Context: ").append(dbContext).append("\n");
        sb.append("Candidates:\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- ID: %s | Name: %s\n", p.getId(), p.getPlaceName()));
        }
        sb.append("Select 3 best places and return JSON with Korean 'reason'.");
        return sb.toString();
    }

    // (3-6) 공통 GMS 호출 및 파싱 (유지)
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
            log.error("GPT Error: {}", e.getMessage());
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

    // 단순 채팅 (유지)
    public String generateChatResponse(String userMessage) {
        // ... (기존 코드 유지) ...
        return "잠시만요...";
    }
}