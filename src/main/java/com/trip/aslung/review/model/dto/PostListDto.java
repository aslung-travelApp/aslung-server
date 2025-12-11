package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class PostListDto {
    private Long postId;
    private String title;
    private String content;        // 간략 설명
    private String thumbnailUrl;   // 썸네일 이미지
    private String regionName;     // 지역 (예: 제주도)
    private int viewCount;
    private int likeCount;
    private double avgRating;      // 평균 별점 (reviews 테이블에서 계산)
    private int reviewCount;       // 리뷰 개수
}