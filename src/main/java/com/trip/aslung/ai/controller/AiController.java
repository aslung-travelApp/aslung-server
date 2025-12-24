package com.trip.aslung.ai.controller;

import com.trip.aslung.ai.model.dto.AiPlaceDto;
import com.trip.aslung.ai.model.dto.AiRequestDto;
import com.trip.aslung.ai.model.service.KakaoService;
import com.trip.aslung.ai.model.service.OpenAiService;
import com.trip.aslung.ai.model.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final WeatherService weatherService;
    private final KakaoService kakaoService;
    private final OpenAiService openAiService;

    @PostMapping("/recommend")
    public ResponseEntity<List<AiPlaceDto>> recommend(@RequestBody AiRequestDto request) {
        System.out.println("1. 요청 받음: " + request.getX() + ", " + request.getY());

        // 1. 날씨
        String weather = weatherService.getCurrentWeather(request.getY(), request.getX());
        System.out.println("2. 날씨 조회 완료: " + weather);

        // 2. 후보군 (맛집, 카페, 관광지)
        List<AiPlaceDto> candidates = new ArrayList<>();
        candidates.addAll(kakaoService.searchCandidates(request.getX(), request.getY(), "FD6"));
        candidates.addAll(kakaoService.searchCandidates(request.getX(), request.getY(), "CE7"));
        candidates.addAll(kakaoService.searchCandidates(request.getX(), request.getY(), "AT4"));

        System.out.println("3. 카카오 후보군 수집 개수: " + candidates.size()); // ★ 여기가 0이면 카카오 문제

        // 3. AI 추천
        List<AiPlaceDto> result = openAiService.getRecommendation(candidates, request, weather);
        System.out.println("4. 최종 추천 결과 개수: " + result.size()); // ★ 여기가 0이면 OpenAI 문제

        return ResponseEntity.ok(result);
    }

    // 채팅 요청 처리
    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody AiRequestDto request) {
        // 서비스로 메시지를 넘기고 응답을 받아옴
        String response = openAiService.generateChatResponse(request.getMessage());
        return ResponseEntity.ok(response);
    }

    // AiController.java

    @PostMapping("/refine")
    public ResponseEntity<List<AiPlaceDto>> refine(@RequestBody AiRequestDto request) {
        log.info("AI 재추천 요청: {}", request.getMessage());
        // 1. 사용자의 채팅 메시지(request.getMessage())를 기반으로 DB 검색 및 재추천
        // 2. 새로운 장소 리스트 반환
        List<AiPlaceDto> newResults = openAiService.refineRecommendations(request.getMessage());
        return ResponseEntity.ok(newResults);
    }
}