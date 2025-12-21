package com.trip.aslung.ai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPlaceDto {
    private String id;          // 카카오 장소 ID
    private String placeName;   // 장소명
    private String category;    // 카테고리
    private String address;     // 주소
    private String x;           // 경도
    private String y;           // 위도
    private String placeUrl;    // 카카오맵 링크

    // AI가 생성해줄 내용
    private String reason;      // 추천 이유
}