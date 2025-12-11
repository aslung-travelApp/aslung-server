package com.trip.aslung.plan.controller;

import com.trip.aslung.plan.model.dto.PlanCreateRequest;
import com.trip.aslung.plan.model.dto.PlanDetailResponse;
import com.trip.aslung.plan.model.dto.PlanListResponse;
import com.trip.aslung.plan.model.dto.PlanUpdateRequest;
import com.trip.aslung.plan.model.service.PlanService;
import jakarta.websocket.server.PathParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

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
}
