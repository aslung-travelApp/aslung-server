package com.trip.aslung.review.model.dto;

import lombok.Data;

@Data
public class PostCommentDto {
    private Long commentId;
    private Long postId;
    private Long userId;
    private String content;
    private String createdAt;

    // 작성자 정보 (Join으로 가져옴)
    private String nickname;
    private String profileImg;
}