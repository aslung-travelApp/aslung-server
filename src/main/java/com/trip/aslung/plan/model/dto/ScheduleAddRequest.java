package com.trip.aslung.plan.model.dto;

import lombok.Data;

@Data
public class ScheduleAddRequest {
    private Long planId;
    private int tripDay;
    private Integer orderIndex;

    // 2. 장소 관련 정보 (Kakao Map에서 오는 데이터)
    private Long placeId;         // (선택) 우리 DB ID가 있다면
    private String kakaoPlaceId;  // (필수) 카카오 장소 ID
    private String placeName;     // 장소명
    private String address;       // 주소
    private double lat;           // mapy (위도)
    private double lng;           // mapx (경도)
    private String category;      // 카테고리
    private String imageUrl;      // 이미지 URL
    private String memo;
}
