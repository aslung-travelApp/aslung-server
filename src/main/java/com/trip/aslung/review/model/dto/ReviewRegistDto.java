package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class ReviewRegistDto {
    private Long postId;          // 게시글 ID
    private Long planScheduleId;  // 스케줄 ID (어떤 일정의 장소인지)
    private Long placeId;         // 장소 ID
    private String comment;       // 리뷰 내용
    private int rating;           // 별점 (1~5)
    private Long userId;       // 유저 ID
}