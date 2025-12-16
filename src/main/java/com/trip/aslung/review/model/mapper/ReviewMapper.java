package com.trip.aslung.review.model.mapper;

import com.trip.aslung.review.model.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param; // [필수 import]
import java.util.List;

@Mapper
public interface ReviewMapper {

    // 1. 리뷰 등록
    int insertReview(ReviewRegistDto reviewDto);

    // 2. 특정 플랜의 리뷰 대상(장소) 목록 조회
    List<ReviewTargetDto> selectReviewTargets(Long planId);

    // 3. 내 리뷰 목록 조회
    List<ReviewResponseDto> selectMyReviews(Long userId);

    // [⚡️수정] 4. 여행기 목록 조회 (검색)
    // XML의 <if test="keyword..."> 에서 'keyword'를 찾기 위해 @Param 필수!
    List<PostListDto> selectPostList(@Param("keyword") String keyword);

    // 5. HOT 여행기 목록 조회
    List<PostListDto> selectHotPostList();

    // [추가] 조회수 1 증가
    int updateViewCount(Long postId);

    // 6. 여행기 상세 기본 정보 조회
    PostDetailDto selectPostDetail(@Param("postId") Long postId, @Param("userId") Long userId);

    // 7. 여행기 상세 스케줄(코스) 조회
    List<PostScheduleDto> selectPostSchedules(Long planId);

    // 8. 여행기 상세 리뷰 목록 조회
    List<ReviewResponseDto> selectReviewsByPostId(Long postId);

    // [추가] 댓글 목록 조회
    List<PostCommentDto> selectPostComments(Long postId);

    // [추가] 댓글 작성
    int insertPostComment(PostCommentDto commentDto);

    // [추가] 댓글 미리보기 (3개)
    List<PostCommentDto> selectPostCommentsPreview(Long postId);

    // [추가] 댓글 수정
    int updatePostComment(PostCommentDto commentDto);

    // [추가] 댓글 삭제
    int deletePostComment(Long commentId);

    // [추가] 좋아요 상태 확인
    int checkPostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // [추가] 좋아요 등록
    int insertPostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // [추가] 좋아요 취소
    int deletePostLike(@Param("postId") Long postId, @Param("userId") Long userId);

    // [추가] 게시글 좋아요 수 증가
    int increaseLikeCount(@Param("postId") Long postId);

    // [추가] 게시글 좋아요 수 감소
    int decreaseLikeCount(@Param("postId") Long postId);

    // [추가] 여행기 삭제
    int deletePost(Long postId);
}