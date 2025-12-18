package com.trip.aslung.plan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Place {
    private Long placeId;         // PK (place_id)
    private String kakaoMapId;    // 카카오맵 고유 ID (kakao_map_id)
    private String name;          // 장소명
    private String address;       // 주소
    private String category;      // 카테고리
    private double latitude;      // 위도 (lat)
    private double longitude;     // 경도 (lng)
    private String imageUrl;      // 이미지 URL
}