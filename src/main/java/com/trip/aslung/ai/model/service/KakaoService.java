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

    /**
     * [기존] 카테고리 코드로 검색 (음식점 FD6 등)
     */
    public List<AiPlaceDto> searchCandidates(String x, String y, String categoryCode) {
        String url = "https://dapi.kakao.com/v2/local/search/category.json";
        return callKakaoApi(url, x, y, categoryCode, null, 2000);
    }

    /**
     * [추가] 키워드로 검색 (예: "박물관", "실내", "카페")
     * Logic RAG에서 확장된 키워드를 여기로 보냅니다.
     */
    public List<AiPlaceDto> searchPlacesByKeyword(String keyword, String x, String y, int radius) {
        String url = "https://dapi.kakao.com/v2/local/search/keyword.json";
        // 키워드 검색은 query 파라미터가 필수입니다.
        return callKakaoApi(url, x, y, null, keyword, radius);
    }

    // 공통 API 호출 로직 분리
    private List<AiPlaceDto> callKakaoApi(String url, String x, String y, String categoryCode, String keyword, int radius) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("x", x)
                .queryParam("y", y)
                .queryParam("radius", radius)
                .queryParam("sort", "distance") // 거리순
                .queryParam("size", 15);

        if (categoryCode != null) builder.queryParam("category_group_code", categoryCode);
        if (keyword != null) builder.queryParam("query", keyword);

        try {
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, entity, Map.class
            );
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("카카오 API 호출 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
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

            // 좌표가 String으로 오므로 DTO 변환 시 주의 (OpenAiService에서 Double 변환 예정)
            list.add(AiPlaceDto.builder()
                    .id((String) doc.get("id"))
                    .placeName((String) doc.get("place_name"))
                    .category((String) doc.get("category_name"))
                    .address(address)
                    .x((String) doc.get("x")) // 경도
                    .y((String) doc.get("y")) // 위도
                    .placeUrl((String) doc.get("place_url"))
                    .build());
        }
        return list;
    }
}