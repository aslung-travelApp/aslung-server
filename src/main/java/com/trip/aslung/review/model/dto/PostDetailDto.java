package com.trip.aslung.review.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class PostDetailDto {
    private Long postId;
    private String title;
    private String content;
    private String thumbnailUrl;
    private int viewCount;
    private int likeCount;
    private boolean liked;
    private String writerNickname;
    private String writerProfileImg;

    private Long planId;
    private String startDate;
    private String endDate;
    private double avgRating;

    private List<PostScheduleDto> schedules;

    // [수정] 기존 reviews(장소리뷰) -> comments(게시글 댓글)로 변경
    // 댓글 미리보기용 (최대 3개 담김)
    private List<PostCommentDto> comments;

    private int commentCount;
}