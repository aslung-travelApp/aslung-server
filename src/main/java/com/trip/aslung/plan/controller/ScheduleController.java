package com.trip.aslung.plan.controller;

import com.trip.aslung.plan.model.dto.ScheduleAddRequest;
import com.trip.aslung.plan.model.dto.ScheduleMoveRequest;
import com.trip.aslung.plan.model.dto.ScheduleUpdateRequest;
import com.trip.aslung.plan.model.service.PlanScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans/{planId}/schedules")
@RequiredArgsConstructor
@Slf4j
public class ScheduleController {

    private final PlanScheduleService planScheduleService;

    // 세부 일정 등록
    @PostMapping
    public ResponseEntity<Void> addSchedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @RequestBody ScheduleAddRequest request
    ){
        planScheduleService.addSchedule(userId, planId, request);
        return ResponseEntity.ok().build();
    }

    // 세부 일정 수정
    @PatchMapping("/{scheduleId}")
    public ResponseEntity<Void> updateSchedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleUpdateRequest request
    ){
        planScheduleService.updateSchedule(userId,planId,scheduleId,request);
        return ResponseEntity.ok().build();
    }

    // 세부 일정 삭제
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @PathVariable Long scheduleId
    ){
        planScheduleService.deleteSchedule(userId,planId,scheduleId);
        return ResponseEntity.ok().build();
    }

    // 순서 변경
    @PatchMapping("/{scheduleId}/move")
    public ResponseEntity<Void> moveScheduleOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleMoveRequest request
    ){
        planScheduleService.moveSchedule(userId,planId,scheduleId,request);
        return ResponseEntity.ok().build();
    }
}