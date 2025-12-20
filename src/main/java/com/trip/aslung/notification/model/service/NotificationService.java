package com.trip.aslung.notification.service;

import com.trip.aslung.notification.model.NotificationType;
import com.trip.aslung.notification.model.dto.NotificationDto;
import com.trip.aslung.notification.model.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    // ★ [핵심] 알림 전송 공통 메서드 (다른 서비스들이 호출)
    @Transactional
    public void send(Long senderId, Long receiverId, NotificationType type, Long targetId, String content) {
        // 나에게 보내는 알림은 저장하지 않음 (선택사항)
        if (senderId.equals(receiverId)) return;

        NotificationDto dto = new NotificationDto();
        dto.setSenderId(senderId);
        dto.setUserId(receiverId);
        dto.setNotificationType(type.name());
        dto.setTargetId(targetId);
        dto.setContent(content);

        // "초대장(PLAN_INVITE)"만 처음에 미완료(false)
        // 나머지(댓글, 좋아요 등)는 버튼이 필요 없으니 처음부터 완료(true)
        boolean isCompleted = !type.equals(NotificationType.PLAN_INVITE);
        dto.setCompleted(isCompleted);

        notificationMapper.insertNotification(dto);
    }

    // 내 알림 목록 조회
    public List<NotificationDto> getMyNotifications(Long userId) {
        return notificationMapper.selectMyNotifications(userId);
    }

    // 알림 처리 완료 (초대 수락/거절 시 호출)
    public void completeNotification(Long notificationId) {
        notificationMapper.updateCompleteStatus(notificationId);
    }

    //  알림 전체 읽음 처리
    @Transactional
    public void readAllNotifications(Long userId) {
        notificationMapper.readAllNotifications(userId);
    }
}