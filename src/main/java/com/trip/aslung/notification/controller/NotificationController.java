package com.trip.aslung.notification.controller;

import com.trip.aslung.notification.model.dto.NotificationDto;
import com.trip.aslung.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    // (보안을 위해 @AuthenticationPrincipal 등을 사용하는 것을 권장하지만, 일단 요청대로 작성)
    @GetMapping("")
    public ResponseEntity<List<NotificationDto>> getMyNotifications(@RequestParam("userId") Long userId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    // (선택) 알림 처리 완료 API (프론트에서 수락/거절 버튼 누른 후 호출)
    @PatchMapping("/{id}/complete")
    public ResponseEntity<String> completeNotification(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int code // 기본값 1(수락/완료)
    ) {
        notificationService.completeNotification(id, code);
        return ResponseEntity.ok("알림 처리 완료");
    }

    // [추가] 알림 전체 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<String> readAllNotifications(@RequestParam Long userId) {
        notificationService.readAllNotifications(userId);
        return ResponseEntity.ok("모든 알림 읽음 처리 완료");
    }
}