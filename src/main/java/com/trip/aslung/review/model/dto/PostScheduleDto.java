package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class PostScheduleDto {
    private int dayNumber;    // N일차
    private int orderIndex;   // 순서
    private String placeName; // 장소명
    private String memo;      // 메모 (설명)
    private String category;  // 카테고리
    private int rating;  // 장소별 리뷰 평점

    // [추가] 리뷰 코멘트 필드
    private String comment;
}