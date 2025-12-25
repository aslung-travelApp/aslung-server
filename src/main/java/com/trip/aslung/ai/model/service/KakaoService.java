package com.trip.aslung.ai.model.service;

import com.trip.aslung.ai.model.dto.AiPlaceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;
    private final RestTemplate restTemplate;

    // 1. 카테고리 검색 (기존 유지)
    public List<AiPlaceDto> searchCandidates(String x, String y, String categoryCode) {
        String url = "https://dapi.kakao.com/v2/local/search/category.json";
        return callKakaoApi(url, x, y, categoryCode, null, 2000);
    }

    // 2. 키워드 검색 (기존 유지)
    public List<AiPlaceDto> searchPlacesByKeyword(String keyword, String x, String y, int radius) {
        String url = "https://dapi.kakao.com/v2/local/search/keyword.json";
        return callKakaoApi(url, x, y, null, keyword, radius);
    }

    // ★ [핵심 수정] 파라미터가 유효할 때만 붙이도록 로직 개선
    private List<AiPlaceDto> callKakaoApi(String url, String x, String y, String categoryCode, String keyword, int radius) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);

        // 필수 파라미터 (키워드 or 카테고리)
        if (categoryCode != null) builder.queryParam("category_group_code", categoryCode);
        if (keyword != null) builder.queryParam("query", keyword);

        // ▼▼▼ 여기가 문제였음! 조건부로 붙여야 함 ▼▼▼
        if (isValidCoordinate(x) && isValidCoordinate(y)) {
            // 좌표가 정상이면 -> 거리순(distance) + 반경(radius) 검색
            builder.queryParam("x", x);
            builder.queryParam("y", y);
            builder.queryParam("radius", radius > 0 ? radius : 20000); // 반경 없으면 20km 기본
            builder.queryParam("sort", "distance");
        } else {
            // 좌표가 없거나 이상하면 -> 정확도순(accuracy) 전국 검색 (에러 방지!)
            builder.queryParam("sort", "accuracy");
        }

        builder.queryParam("size", 15);

        try {
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, entity, Map.class
            );
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("카카오 API 호출 실패 (url={}): {}", builder.toUriString(), e.getMessage());
            return new ArrayList<>();
        }
    }

    // 좌표값 유효성 검사 (null, empty, "null" 문자열 체크)
    private boolean isValidCoordinate(String coord) {
        return coord != null && !coord.isEmpty() && !coord.equals("null");
    }

    private List<AiPlaceDto> parseResponse(Map body) {
        List<AiPlaceDto> list = new ArrayList<>();
        if (body == null || body.get("documents") == null) return list;

        List<Map<String, Object>> documents = (List<Map<String, Object>>) body.get("documents");
        for (Map<String, Object> doc : documents) {
            String address = (String) doc.get("road_address_name");
            if (address == null || address.isEmpty()) {
                address = (String) doc.get("address_name");
            }

            list.add(AiPlaceDto.builder()
                    .id((String) doc.get("id"))
                    .placeName((String) doc.get("place_name"))
                    .category((String) doc.get("category_name"))
                    .address(address)
                    .x((String) doc.get("x"))
                    .y((String) doc.get("y"))
                    .placeUrl((String) doc.get("place_url"))
                    .build());
        }
        return list;
    }
}