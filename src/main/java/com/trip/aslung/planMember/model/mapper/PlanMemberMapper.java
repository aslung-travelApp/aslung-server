package com.trip.aslung.planMember.model.mapper;

import com.trip.aslung.planMember.model.dto.InvitationResponse;
import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.planMember.model.dto.PlanMemberResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlanMemberMapper {
    List<PlanMemberResponse> findMembersByPlanId(@Param("planId") Long planId);
    void insertPlanMember(PlanMember planMember);
    void deleteMember(Long planId, Long userId);
    void updateMemberStatus(Long planId, Long userId, String status);
    PlanMember findByPlanIdAndUserId(Long planId, Long userId);
    boolean existsByPlanIdAndUserId(Long planId, Long userId);
    PlanMember findById(Long memberId);
    List<InvitationResponse> findInvitationsByUserId(Long userId);
}