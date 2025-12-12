package com.trip.aslung.plan.model.mapper;

import com.trip.aslung.plan.model.dto.PlanMember;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface PlanMemberMapper {
    void insertPlanMember(PlanMember planMember);
    PlanMember findByPlanIdAndUserId(Long planId, Long userId);
}