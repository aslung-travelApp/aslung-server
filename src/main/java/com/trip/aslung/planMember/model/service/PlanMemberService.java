package com.trip.aslung.planMember.model.service;

import com.trip.aslung.planMember.model.dto.InvitationResponse;
import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.planMember.model.dto.PlanMemberResponse;

import java.util.List;

public interface PlanMemberService {
    List<PlanMemberResponse> getMember(Long userId, Long planId);
    void inviteMember(Long planId, Long ownerId, Long targetUserId);
    void kickMember(Long planId, Long ownerId, Long targetMemberId);
    void leavePlan(Long planId, Long userId);
    void acceptInvitation(Long planId, Long userId);
    List<InvitationResponse> getMyInvitations(Long userId);
}