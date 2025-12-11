package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class ReviewTargetDto {
    private Long planScheduleId; // 스케줄 ID (리뷰 작성 시 필요)
    private Long placeId;        // 장소 ID
    private String placeName;    // 장소 이름 (화면 표시용)
    private int dayNumber;       // N일차
    private int orderIndex;      // 순서
}