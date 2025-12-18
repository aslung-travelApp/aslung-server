package com.trip.aslung.plan.controller;

import com.trip.aslung.plan.model.dto.ScheduleAddRequest;
import com.trip.aslung.plan.model.dto.ScheduleMoveRequest;
import com.trip.aslung.plan.model.dto.ScheduleUpdateRequest;
import com.trip.aslung.plan.model.service.PlanScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ScheduleSocketController {

    private final PlanScheduleService planScheduleService;
    private final SimpMessagingTemplate messagingTemplate;

    // 세부 일정 등록
    @MessageMapping("/plans/{planId}/schedules/add")
    public void addSchedule(
            @DestinationVariable Long planId,
            ScheduleAddRequest request,
            Principal principal // Security 설정 시 인증된 사용자 ID를 가져올 수 있음
    ) {
        // userId 추출 (Security 설정에 따라 principal.getName() 등 활용)
        Long userId = Long.parseLong(principal.getName());

        planScheduleService.addSchedule(userId, planId, request);

        // 변경 사항을 해당 플랜을 구독 중인 모든 사용자에게 전송
        messagingTemplate.convertAndSend("/sub/plans/" + planId, "SCHEDULE_ADDED");
    }

    // 세부 일정 수정
    @MessageMapping("/plans/{planId}/schedules/{scheduleId}/update")
    public void updateSchedule(
            @DestinationVariable Long planId,
            @DestinationVariable Long scheduleId,
            ScheduleUpdateRequest request,
            Principal principal
    ) {
        Long userId = Long.parseLong(principal.getName());
        planScheduleService.updateSchedule(userId, planId, scheduleId, request);

        messagingTemplate.convertAndSend("/sub/plans/" + planId, "SCHEDULE_UPDATED");
    }

    // 세부 일정 삭제
    @MessageMapping("/plans/{planId}/schedules/{scheduleId}/delete")
    public void deleteSchedule(
            @DestinationVariable Long planId,
            @DestinationVariable Long scheduleId,
            Principal principal
    ) {
        Long userId = Long.parseLong(principal.getName());
        planScheduleService.deleteSchedule(userId, planId, scheduleId);

        messagingTemplate.convertAndSend("/sub/plans/" + planId, "SCHEDULE_DELETED");
    }

    // 순서 변경
    @MessageMapping("/plans/{planId}/schedules/{scheduleId}/move")
    public void moveScheduleOrder(
            @DestinationVariable Long planId,
            @DestinationVariable Long scheduleId,
            ScheduleMoveRequest request,
            Principal principal
    ) {
        Long userId = Long.parseLong(principal.getName());
        planScheduleService.moveSchedule(userId, planId, scheduleId, request);

        messagingTemplate.convertAndSend("/sub/plans/" + planId, "SCHEDULE_MOVED");
    }

    // 새로고침 신호 전달
    @MessageMapping("/plans/{planId}/update")
    public void broadcastUpdate(@DestinationVariable Long planId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/plans/" + planId, payload);
    }
}