package com.trip.aslung.plan.model.service;

import com.trip.aslung.plan.model.dto.PlanCreateRequest;
import com.trip.aslung.plan.model.dto.PlanDetailResponse;
import com.trip.aslung.plan.model.dto.PlanListResponse;
import com.trip.aslung.plan.model.dto.PlanUpdateRequest;

import java.util.List;

public interface PlanService {
    List<PlanListResponse> getMyPlans(Long userId);
    PlanDetailResponse getPlanDetail(Long userId, Long planId);
    Long createPlan(Long userId, PlanCreateRequest request);
    void updatePlan(Long userId, Long planId, PlanUpdateRequest request);
    void deletePlan(Long userId, Long planId);

    void updateVisibility(Long planId, Long userId, boolean isPublic);

    // [추가] 플랜 복사 메서드 정의
    long copyPlan(Long sourcePlanId, Long userId);
}
