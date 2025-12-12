package com.trip.aslung.plan.model.mapper;

import com.trip.aslung.plan.model.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlanMapper {
    List<PlanListResponse> selectMyPlans(Long userId);
    PlanDetailResponse selectPlanDetail(@Param("planId") Long planId);
    List<PlanMember> selectPlanMembers(Long planId);
    List<PlanSchedule> selectPlanSchedules(Long planId);
    void insertPlan(Plan plan);
    void updatePlan(Plan plan);
    void deletePlan(Long planId);
}
