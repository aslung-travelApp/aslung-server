package com.trip.aslung.review.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ReviewUpdateDto {
    private Long postId;        // 수정할 게시글 ID
    private String title;       // 수정할 제목
    private String content;     // 수정할 내용
    private List<PlaceReviewUpdateDto> placeReviews; // 수정할 장소별 리뷰 목록

    @Data
    public static class PlaceReviewUpdateDto {
        private Long planScheduleId; // 어떤 스케줄(장소)의 리뷰인지 식별
        private int rating;          // 수정할 별점
        private String comment;      // 수정할 한줄평
    }
}