package com.trip.aslung.plan.controller;

import com.trip.aslung.plan.model.dto.*;
import com.trip.aslung.plan.model.service.PlanService;
import com.trip.aslung.planMember.model.dto.InvitationResponse;
import com.trip.aslung.planMember.model.service.PlanMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Slf4j
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final PlanMemberService planMemberService;

    @GetMapping
    public ResponseEntity<List<PlanListResponse>> getMyPlans(
            @AuthenticationPrincipal Long userId
    ){
        List<PlanListResponse> myPlans = planService.getMyPlans(1L);
        return ResponseEntity.ok(myPlans);
    }

    @PostMapping
    public ResponseEntity<Long> createPlan(
            @AuthenticationPrincipal Long userId,
            @RequestBody PlanCreateRequest request
    ){
        Long planId = planService.createPlan(userId, request);
        return ResponseEntity.ok(planId);
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanDetailResponse> getPlanDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable("planId") Long planId
    ){
        PlanDetailResponse planDetail = planService.getPlanDetail(userId, planId);
        return ResponseEntity.ok(planDetail);
    }

    @PatchMapping("/{planId}")
    public ResponseEntity<Void> updatePlan(
            @AuthenticationPrincipal Long userId,
            @PathVariable(("planId")) Long planId,
            @RequestBody PlanUpdateRequest request
    ){
        planService.updatePlan(userId, planId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(
            @AuthenticationPrincipal Long userId,
            @PathVariable(("planId")) Long planId
    ){
        planService.deletePlan(userId, planId);
        return ResponseEntity.ok().build();
    }
    // 플랜 공개여부 수정
    @PatchMapping("/{planId}/visibility")
    public ResponseEntity<String> changeVisibilty(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @RequestBody PlanVisibilityRequest request
    ){
        planService.updateVisibility(planId, userId, request.isPublic());

        String status = request.isPublic() ? "공개" : "비공개";
        return ResponseEntity.ok("여행 계획이 " + status + "상태로 변경되었습니다");
    }
    // 초대장 리스트
    @GetMapping("/invitations")
    public ResponseEntity<List<InvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal Long userId
    ){
        log.info("로그인 사용자 ID" + userId);
        List<InvitationResponse> invitations = planMemberService.getMyInvitations(userId);
        return ResponseEntity.ok(invitations);
    }
}
