package com.trip.aslung.notification.model;

public enum NotificationType {
    PLAN_INVITE,   // 초대 (수락/거절 버튼 필요)
    PLAN_ACCEPT,   // 초대 수락 알림
    PLAN_DECLINE,  // 초대 거절 알림
    POST_COMMENT,  // 댓글 알림
    POST_LIKE      // 좋아요 알림
}