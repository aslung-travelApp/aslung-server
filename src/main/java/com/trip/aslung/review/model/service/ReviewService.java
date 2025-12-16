package com.trip.aslung.review.model.service;

import com.trip.aslung.review.model.dto.*;
import com.trip.aslung.review.model.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // [추가] List 사용을 위해 필요

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;

    // 기존: 리뷰 등록
    @Transactional
    public void registReview(ReviewRegistDto reviewDto) {
        reviewMapper.insertReview(reviewDto);
    }

    // [추가] 특정 플랜의 리뷰 대상(장소) 목록 조회
    // 조회 전용이므로 readOnly = true를 주면 성능 최적화에 도움이 됩니다.
    @Transactional(readOnly = true)
    public List<ReviewTargetDto> getReviewTargets(Long planId) {
        return reviewMapper.selectReviewTargets(planId);
    }

    @Transactional(readOnly = true)
    public List<PostListDto> getPostList(String keyword) {
        return reviewMapper.selectPostList(keyword);
    }

    @Transactional(readOnly = true)
    public List<PostListDto> getHotPostList() {
        return reviewMapper.selectHotPostList();
    }

    @Transactional
    public PostDetailDto getPostDetail(Long postId, Long userId) {
        reviewMapper.updateViewCount(postId);

        // 로그인 안 했을 경우 0으로 처리하여 에러 방지
        Long safeUserId = (userId == null) ? 0L : userId;

        PostDetailDto post = reviewMapper.selectPostDetail(postId, safeUserId);

        if (post == null) return null;

        if (post.getPlanId() != null) {
            // [확인] 여기서 수정된 XML 쿼리가 실행되어 리뷰 코멘트가 memo로 들어감
            post.setSchedules(reviewMapper.selectPostSchedules(post.getPlanId()));
        }

        // [수정] 장소 리뷰 대신 -> 게시글 댓글(미리보기 3개)을 가져와서 넣음
        post.setComments(reviewMapper.selectPostCommentsPreview(postId));

        return post;
    }

    // 좋아요 토글 로직
    @Transactional
    public boolean togglePostLike(Long postId, Long userId) {
        int count = reviewMapper.checkPostLike(postId, userId);

        if (count > 0) {
            reviewMapper.deletePostLike(postId, userId);
            reviewMapper.decreaseLikeCount(postId);
            return false;
        } else {
            reviewMapper.insertPostLike(postId, userId);
            reviewMapper.increaseLikeCount(postId);
            return true;
        }
    }

    // 댓글 목록
    @Transactional(readOnly = true)
    public List<PostCommentDto> getPostComments(Long postId) {
        return reviewMapper.selectPostComments(postId);
    }

    // 댓글 등록
    @Transactional
    public void registPostComment(PostCommentDto commentDto) {
        reviewMapper.insertPostComment(commentDto);
    }

    // 댓글 수정
    @Transactional
    public void modifyPostComment(PostCommentDto commentDto) {
        reviewMapper.updatePostComment(commentDto);
    }

    // 댓글 삭제
    @Transactional
    public void removePostComment(Long commentId) {
        reviewMapper.deletePostComment(commentId);
    }

    // 여행기 삭제
    @Transactional
    public void removePost(Long postId, Long userId) {
        String role = reviewMapper.selectUserRole(userId);

        int result = 0;

        if ("ADMIN".equals(role)) {
            result = reviewMapper.deletePostByAdmin(postId);
        }
        else {
            result = reviewMapper.deletePost(postId, userId);
        }

        if (result == 0) {
            throw new RuntimeException("삭제 권한이 없거나 존재하지 않는 게시글입니다.");
        }
    }
}