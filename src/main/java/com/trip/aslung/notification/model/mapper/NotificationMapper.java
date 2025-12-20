package com.trip.aslung.notification.model.mapper;

import com.trip.aslung.notification.model.dto.NotificationDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface NotificationMapper {

    // 알림 저장
    void insertNotification(NotificationDto notificationDto);

    // 내 알림 목록 조회 (보낸 사람 정보 포함)
    List<NotificationDto> selectMyNotifications(Long userId);

    // 초대 처리 완료 상태 변경
    void updateCompleteStatus(Long notificationId);

    // 알림 읽음 처리
    void readAllNotifications(Long userId);
}