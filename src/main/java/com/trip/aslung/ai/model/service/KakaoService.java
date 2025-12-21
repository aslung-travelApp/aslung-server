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
                .queryParam("radius", 2000)
                .queryParam("sort", "accuracy")
                .queryParam("size", 5);

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
            list.add(AiPlaceDto.builder()
                    .id((String) doc.get("id"))
                    .placeName((String) doc.get("place_name"))
                    .category((String) doc.get("category_group_name"))
                    .address((String) doc.get("address_name"))
                    .x((String) doc.get("x"))
                    .y((String) doc.get("y"))
                    .placeUrl((String) doc.get("place_url"))
                    .build());
        }
        return list;
    }
}