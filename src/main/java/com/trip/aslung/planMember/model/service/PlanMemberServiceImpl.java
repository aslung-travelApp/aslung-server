package com.trip.aslung.planMember.model.service;

import com.trip.aslung.plan.model.dto.PlanDetailResponse;
import com.trip.aslung.plan.model.mapper.PlanMapper;
import com.trip.aslung.planMember.model.dto.InvitationResponse;
import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.planMember.model.mapper.PlanMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class PlanMemberServiceImpl implements PlanMemberService {

    private final PlanMemberMapper planMemberMapper;
    private final PlanMapper planMapper;

    @Override
    public void inviteMember(Long planId, Long ownerId, Long targetUserId) {
        PlanDetailResponse plan = planMapper.selectPlanDetail(planId);
        if(plan == null) throw new IllegalArgumentException("여행 계획을 찾을 수 없습니다.");

        if(!plan.getIsPublic()) throw new IllegalStateException("비공개 여행 계획은 멤버를 초대할 수 없습니다.");

        PlanMember requester = planMemberMapper.findByPlanIdAndUserId(planId,ownerId);
        if (!requester.getRole().equals("OWNER")) throw new SecurityException("일행 초대는 방장만 가능합니다.");
        if(ownerId.equals(targetUserId)) throw new IllegalArgumentException("본인은 초대 불가능");

        if(planMemberMapper.existsByPlanIdAndUserId(planId, targetUserId)){
            throw new IllegalArgumentException("이미 참여 중이거나 초대된 사용자입니다.");
        }

        PlanMember newMember = PlanMember.builder()
                .planId(planId)
                .userId(targetUserId)
                .role("EDITOR")
                .status("INVITED")
                .build();

        planMemberMapper.insertPlanMember(newMember);
        log.info("초대 성공: planId={}, inviter={}, target={}", planId, ownerId, targetUserId);
    }

    @Override
    public void acceptInvitation(Long planId, Long userId) {
        PlanMember me = planMemberMapper.findByPlanIdAndUserId(planId,userId);
        if(me == null) throw new IllegalArgumentException("초대 정보를 찾을 수 없습니다.");

        if(!me.getStatus().equals("INVITED")){
            throw new IllegalStateException("이미 참여 중이거나 유효하지 않은 초대입니다.");
        }

        planMemberMapper.updateMemberStatus(planId, userId, "JOINED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> getMyInvitations(Long userId) {
        return planMemberMapper.findInvitationsByUserId(userId);
    }

    @Override
    public void kickMember(Long planId, Long ownerId, Long targetMemberId) {
        PlanMember me = planMemberMapper.findByPlanIdAndUserId(planId, ownerId);
        if(me == null) throw new IllegalArgumentException("정보를 찾을 수 없습니다.");
        // OWNER만 가능
        if(!me.getRole().equals("OWNER")) throw new SecurityException("멤버 강퇴는 방장만 가능합니다."); // 403 Forbidden

        PlanMember target = planMemberMapper.findByPlanIdAndUserId(planId, targetMemberId);
        if(target == null) throw new IllegalArgumentException("존재하지 않는 멤버입니다.");

        log.info("plan id : {}, target plan id : {}", planId, target.getPlanId());
        if(!target.getPlanId().equals(planId)) throw new IllegalArgumentException("해당 여행 계획의 멤버가 아닙니다.");

        if(target.getRole().equals("OWNER")) throw new IllegalArgumentException("방장은 내보낼 수 없습니다.");

        planMemberMapper.deleteMember(planId, target.getUserId());
    }
    @Override
    public void leavePlan(Long planId, Long userId) {
        PlanMember me = planMemberMapper.findByPlanIdAndUserId(planId,userId);
        if(me == null) throw new IllegalArgumentException("초대 정보를 찾을 수 없습니다.");

        if(me.getRole().equals("OWNER")) throw new IllegalArgumentException("방장은 여행 계획을 나갈 수 없습니다.");

        planMemberMapper.deleteMember(planId, userId);
    }
}
