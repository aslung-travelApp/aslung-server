package com.trip.aslung.ai.model.service;

import com.trip.aslung.ai.model.dto.AiPlaceDto;
import lombok.RequiredArgsConstructor;
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
public class KakaoService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;
    private final RestTemplate restTemplate;

    public List<AiPlaceDto> searchCandidates(String x, String y, String categoryCode) {
        String url = "https://dapi.kakao.com/v2/local/search/category.json";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", x)
                .queryParam("y", y)
                .queryParam("radius", 2000)     // 2km 반경
                .queryParam("sort", "distance") // ★ 수정: accuracy -> distance (거리순이 핵심!)
                .queryParam("size", 15);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, Map.class
        );

        return parseResponse(response.getBody());
    }

    private List<AiPlaceDto> parseResponse(Map body) {
        List<AiPlaceDto> list = new ArrayList<>();
        if (body == null || body.get("documents") == null) return list;

        List<Map<String, Object>> documents = (List<Map<String, Object>>) body.get("documents");
        for (Map<String, Object> doc : documents) {

            // ★ 수정: 도로명 주소가 있으면 도로명, 없으면 지번 주소 사용 (Vue 화면용)
            String address = (String) doc.get("road_address_name");
            if (address == null || address.isEmpty()) {
                address = (String) doc.get("address_name");
            }

            list.add(AiPlaceDto.builder()
                    .id((String) doc.get("id"))
                    .placeName((String) doc.get("place_name"))
                    // ★ 수정: '음식점'보다 '한식', '일식' 같이 상세 카테고리가 뱃지에 더 예쁨
                    .category((String) doc.get("category_name"))
                    .address(address) // 위에서 만든 주소 변수
                    .x((String) doc.get("x"))
                    .y((String) doc.get("y"))
                    .placeUrl((String) doc.get("place_url"))
                    .build());
        }
        return list;
    }
}