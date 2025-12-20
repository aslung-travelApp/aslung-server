package com.trip.aslung.review.model.service;

import com.trip.aslung.notification.model.dto.NotificationDto;
import com.trip.aslung.notification.model.mapper.NotificationMapper;
import com.trip.aslung.review.model.dto.*;
import com.trip.aslung.review.model.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // [추가] List 사용을 위해 필요

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;

    private final NotificationMapper notificationMapper;

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
            // 좋아요 취소 (알림 X)
            reviewMapper.deletePostLike(postId, userId);
            reviewMapper.decreaseLikeCount(postId);
            return false;
        } else {
            // 좋아요 등록 (알림 O)
            reviewMapper.insertPostLike(postId, userId);
            reviewMapper.increaseLikeCount(postId);

            // =========================================================
            //  [수정됨] 알림 발송 로직
            // =========================================================
            try {
                // 1. 방금 만든 메서드로 '작성자 ID'만 가볍게 조회!
                Long writerId = reviewMapper.selectWriterId(postId);

                // 2. 작성자가 존재하고, 본인이 쓴 글이 아닐 때만 알림 전송
                if (writerId != null && !writerId.equals(userId)) {

                    NotificationDto notification = new NotificationDto();
                    notification.setUserId(writerId);        // 받는 사람 (작성자)
                    notification.setSenderId(userId);        // 보낸 사람 (좋아요 누른 사람)
                    notification.setNotificationType("POST_LIKE"); // 알림 타입
                    notification.setTargetId(postId);        // 클릭 시 이동할 게시글 ID
                    notification.setContent("님이 회원님의 여행기를 좋아합니다."); // 메시지

                    // 3. DB 저장
                    notificationMapper.insertNotification(notification);
                }
            } catch (Exception e) {
                log.error("좋아요 알림 전송 실패: ", e);
            }
            // =========================================================

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

        try {
            Long postId = commentDto.getPostId();
            Long commenterId = commentDto.getUserId();

            Long writerId = reviewMapper.selectWriterId(postId);

            String postTitle = reviewMapper.selectPostTitle(postId);
            if(postTitle == null) postTitle = "여행기";

            if (writerId != null && !writerId.equals(commenterId)) {

                NotificationDto notification = new NotificationDto();
                notification.setUserId(writerId);
                notification.setSenderId(commenterId);

                notification.setNotificationType("POST_COMMENT");

                notification.setTargetId(postId);
                // ★ 핵심: [메인 메시지] || [댓글 내용] 형태로 합쳐서 저장
                // 예: "님이 회원님의 '부산 여행' 여행기에 댓글을 남겼습니다.||와 여기 진짜 좋네요!"
                String message = "님이 회원님의 " + postTitle + "에 댓글을 남겼습니다.";
                String preview = commentDto.getContent();

                // 댓글이 너무 길면 20자 정도로 자르기
                if(preview.length() > 20) {
                    preview = preview.substring(0, 20) + "...";
                }

                notification.setContent(message + "||" + preview);

                // 3. DB 저장
                notificationMapper.insertNotification(notification);
            }
        } catch (Exception e) {
            // 알림 전송 실패해도 댓글 등록은 유지되도록 예외 처리
            log.error("댓글 알림 전송 실패: ", e);
        }
        // =========================================================
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

    // [추가] 여행기 등록 로직 (트랜잭션 필수!)
    @Transactional
    public void registTripPost(TripPostRegistDto registDto) {

        // 1. 게시글(TripPost) 먼저 저장
        // 이 시점에 DB가 생성한 ID가 registDto.getPostId()에 담깁니다.
        reviewMapper.insertPost(registDto);

        // 2. 장소별 리뷰(PlaceReviews) 리스트 처리
        List<TripPostRegistDto.PlaceReviewDto> reviews = registDto.getPlaceReviews();

        if (reviews != null && !reviews.isEmpty()) {

            // ★★★ [가장 중요한 부분] ★★★
            // 리스트를 한 바퀴 돌면서 "너네 부모(게시글) 번호는 이거야!"라고 알려줘야 합니다.
            for (TripPostRegistDto.PlaceReviewDto review : reviews) {
                review.setPostId(registDto.getPostId());
                review.setUserId(registDto.getUserId());
            }

            // 3. ID가 채워진 리스트를 한 번에 저장
            // (인자를 3개 보내지 말고, 리스트 하나만 보냅니다)
            reviewMapper.insertTripReviews(reviews);
        }
    }

    @Transactional(readOnly = true)
    public List<PostListDto> getMyPostList(Long userId) {
        // 매퍼 호출
        return reviewMapper.selectMyPostList(userId);
    }

    // [추가] 여행기 수정 비즈니스 로직
    @Transactional
    public void updateReview(Long userId, ReviewUpdateDto requestDto) {
        // 1. 본인 확인 (이 글이 내 글인지?)
        // (간단하게 구현하기 위해 Mapper에서 user_id 조건으로 검사하거나, 여기서 조회 후 비교)
        // 여기서는 Update 쿼리에 userId 조건을 넣어 처리하겠습니다.

        // 2. 게시글(Posts) 테이블 수정
        int updatedRows = reviewMapper.updatePost(requestDto.getPostId(), userId, requestDto.getTitle(), requestDto.getContent());

        if (updatedRows == 0) {
            throw new RuntimeException("게시글을 찾을 수 없거나 권한이 없습니다.");
        }

        // 3. 장소별 리뷰(Reviews) 테이블 수정
        // 리스트를 돌면서 하나씩 업데이트합니다.
        for (ReviewUpdateDto.PlaceReviewUpdateDto reviewDto : requestDto.getPlaceReviews()) {
            reviewMapper.updatePlaceReview(
                    requestDto.getPostId(),
                    reviewDto.getPlanScheduleId(),
                    reviewDto.getRating(),
                    reviewDto.getComment()
            );
        }
    }

    // [추가] 좋아요한 게시글 목록 가져오기
    @Transactional
    public List<PostListDto> getLikedPostList(Long userId) {
        return reviewMapper.selectLikedPostList(userId);
    }
}