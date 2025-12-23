package com.trip.aslung.ai.controller;

import com.trip.aslung.ai.model.dto.AiPlaceDto;
import com.trip.aslung.ai.model.service.KakaoService;
import com.trip.aslung.ai.model.service.OpenAiService;
import com.trip.aslung.ai.model.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
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
}