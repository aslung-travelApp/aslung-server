package com.trip.aslung.notification.model.dto;

import lombok.Data;

@Data
public class NotificationDto {
    private Long notificationId;

    private Long userId;        // 받는 사람 (user_id)
    private Long senderId;      // 보낸 사람 (sender_id)

    // ★ DB 조인으로 가져올 보낸 사람 정보
    private String senderNickname;
    private String senderProfileImg;

    private String notificationType; // Enum -> String 저장
    private Long targetId;           // plan_id 또는 post_id
    private String content;          // 알림 메시지

    private boolean isCompleted;     // 버튼 처리 여부 (초대장용)
    private boolean isRead;          // 읽음 여부
    private String createdAt;
}