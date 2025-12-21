package com.trip.aslung.ai.model.service;

import com.fasterxml.jackson.databind.ObjectMapper; // ★ JSON 변환기 추가
import com.trip.aslung.ai.model.dto.AiPlaceDto;
import com.trip.aslung.ai.model.dto.AiRequestDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${openai.api.url}")
    private String openAiUrl;

    @Value("${openai.model}")
    private String modelName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // Spring이 자동으로 주입해줌

    public List<AiPlaceDto> getRecommendation(List<AiPlaceDto> candidates, AiRequestDto request, String weather) {
        // 1. 헤더 설정 (브라우저인 척 위장하기 + 한글 깨짐 방지)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8)); // UTF-8 강제
        headers.set("Authorization", "Bearer " + openAiKey);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"); // ★ 중요! 크롬인 척

        // 2. 프롬프트 생성
        String prompt = createPrompt(candidates, request, weather);

        // 3. 요청 DTO 생성
        GptRequest gptRequest = new GptRequest(
                modelName,
                List.of(
                        new GptMessage("system", """
    You are a professional local travel guide in Korea.
    Respond in strictly valid JSON format only.
    
    [CRITICAL RULES]
    1. All languages (name, address, description, reason) MUST be in Korean (한국어).
    2. Do NOT use any English in the output values.
    3. The 'description' should be emotional, engaging, and around 2~3 sentences.
    4. Do NOT include Markdown formatting (like ```json). Just return raw JSON.
    """),
                        new GptMessage("user", prompt)
                ),
                1000,
                0.7
        );

        try {
            // ★ JSON 변환 과정을 우리가 직접 통제 (로그 찍기 위해)
            String jsonBody = objectMapper.writeValueAsString(gptRequest);
            log.info("▶ GPT에게 보낼 데이터: {}", jsonBody); // 로그 확인용

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            // 4. 요청 전송
            // 응답도 String으로 받아서 로그로 확인
            ResponseEntity<String> response = restTemplate.exchange(openAiUrl, HttpMethod.POST, entity, String.class);
            log.info("◀ GPT 응답 데이터: {}", response.getBody());

            // 응답 파싱 (String -> Map)
            Map responseMap = objectMapper.readValue(response.getBody(), Map.class);
            return parseGptResponse(responseMap, candidates);

        } catch (Exception e) {
            log.error("🚨 GPT 호출 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // --- [내부 DTO] ---
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GptRequest {
        private String model;
        private List<GptMessage> messages;
        private int max_tokens;
        private double temperature;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GptMessage {
        private String role;
        private String content;
    }
    // -----------------

    private String createPrompt(List<AiPlaceDto> candidates, AiRequestDto req, String weather) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Weather: ").append(weather).append("\n");
        sb.append("Companion: ").append(req.getCompanion()).append("\n");
        sb.append("Style: ").append(String.join(", ", req.getStyles())).append("\n");
        sb.append("Type: ").append(req.getType()).append("\n\n");

        sb.append("Candidate Places:\n");
        for (AiPlaceDto p : candidates) {
            sb.append(String.format("- [%s] %s (%s)\n", p.getId(), p.getPlaceName(), p.getCategory()));
        }

        sb.append("\nSelect best places based on weather and style.\n");
        if ("COURSE".equals(req.getType())) {
            sb.append("Select 3 places for a course (Meal -> Cafe -> Tour).\n");
        } else {
            sb.append("Select 1 best place.\n");
        }
        sb.append("IMPORTANT: Return ONLY JSON format like this: { \"recommendations\": [ { \"id\": \"...\", \"reason\": \"...\" } ] }");

        return sb.toString();
    }

    private List<AiPlaceDto> parseGptResponse(Map response, List<AiPlaceDto> candidates) {
        try {
            // 1. GPT 응답 구조: choices -> message -> content
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");

            // 2. content는 String 형태의 JSON이므로, 다시 Map으로 변환
            // 예: "{ \"recommendations\": [ ... ] }"
            Map<String, Object> contentMap = objectMapper.readValue(content, Map.class);
            List<Map<String, String>> recommendations = (List<Map<String, String>>) contentMap.get("recommendations");

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