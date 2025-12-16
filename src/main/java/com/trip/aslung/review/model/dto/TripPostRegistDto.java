package com.trip.aslung.review.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripPostRegistDto {
    private Long postId;        // DB에서 생성한 게시글 번호
    private Long planId;        // 어떤 플랜인지
    private Long userId;        // 누가 썼는지
    private String title;       // 여행기 제목
    private String content;     // 여행기 전체 총평(본문)
    private String regionName;  // 지역명

    // 각 장소별 리뷰 리스트
    private List<PlaceReviewDto> placeReviews;

    @Data
    public static class PlaceReviewDto {
        private Long planScheduleId; // 일정 스케줄 ID
        private Long placeId;        // 장소 ID
        private int rating;          // 별점
        private String comment;      // 한줄평
    }
}