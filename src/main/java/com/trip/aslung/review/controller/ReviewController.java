package com.trip.aslung.review.controller;

import com.trip.aslung.review.model.dto.PostCommentDto;
import com.trip.aslung.review.model.dto.ReviewRegistDto;
import com.trip.aslung.review.model.dto.ReviewTargetDto;
import com.trip.aslung.review.model.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody ReviewRegistDto reviewDto) {
        // TODO: 실제로는 SecurityContextHolder 등에서 로그인한 userId를 가져와야 함.
        // 현재는 DB 테스트를 위해 넘어가는 값만 저장합니다.

        try {
            reviewService.registReview(reviewDto);
            return new ResponseEntity<>(HttpStatus.CREATED);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getReviewTargets(@PathVariable Long planId) {
        try {
            List<ReviewTargetDto> targets = reviewService.getReviewTargets(planId);
            return ResponseEntity.ok(targets);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 여행기 목록 (검색 포함)
    @GetMapping("/posts")
    public ResponseEntity<?> getPostList(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(reviewService.getPostList(keyword));
    }

    // HOT 여행기
    @GetMapping("/posts/hot")
    public ResponseEntity<?> getHotPostList() {
        return ResponseEntity.ok(reviewService.getHotPostList());
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<?> getPostDetail(@PathVariable Long postId) {
        return ResponseEntity.ok(reviewService.getPostDetail(postId));
    }

    // 댓글 목록 조회 API
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<?> getPostComments(@PathVariable Long postId) {
        return ResponseEntity.ok(reviewService.getPostComments(postId));
    }

    // 댓글 등록 API
    @PostMapping("/posts/comments")
    public ResponseEntity<?> registPostComment(@RequestBody PostCommentDto commentDto) {
        reviewService.registPostComment(commentDto);
        return ResponseEntity.ok().build();
    }
}