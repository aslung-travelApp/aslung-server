package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class ReviewResponseDto {
    private Long reviewId;
    private String placeName;   // 장소 이름
    private String comment;     // 리뷰 내용
    private int rating;         // 별점
    private String planTitle;   // 여행기 제목 (어떤 여행에서 쓴 건지)
    private String createdAt;   // 작성일
}