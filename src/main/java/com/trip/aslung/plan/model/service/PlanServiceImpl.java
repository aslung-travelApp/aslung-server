package com.trip.aslung.plan.model.service;

import com.trip.aslung.plan.model.dto.*;
import com.trip.aslung.plan.model.mapper.PlanMapper;
import com.trip.aslung.plan.model.mapper.PlanScheduleMapper;
import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.planMember.model.mapper.PlanMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final PlanMapper planMapper;
    private final PlanMemberMapper planMemberMapper;
    private final PlanScheduleMapper planScheduleMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlanListResponse> getMyPlans(Long userId) {
        return planMapper.selectMyPlans(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanDetailResponse getPlanDetail(Long userId, Long planId) {
        // 1. 플랜 가져오기
        PlanDetailResponse plan = planMapper.selectPlanDetail(planId);

        if(plan==null){
            throw new IllegalArgumentException("해당 플랜이 존재하지 않습니다.");
        }

        // 2. 멤버 조회
        List<PlanMember> members = planMapper.selectPlanMembers(planId);

        // 3. 권한 체크
        boolean isOwner = plan.getOwnerId().equals(userId);
        boolean isMember = members.stream()
                .anyMatch(m -> m.getUserId().equals(userId) && "JOINED".equals(m.getStatus()));

//        if(!Boolean.TRUE.equals(plan.getIsPublic()) && !isMember && !isOwner){
//            throw new IllegalArgumentException("접근 권한이 없습니다");
//        }

        // 4. 스케줄 조회
        List<PlanSchedule> schedules = planScheduleMapper.selectSchedulesByPlanId(planId);

        plan.setMembers(members);
        plan.setSchedules(schedules);

        return plan;
    }

    @Override
    @Transactional
    public Long createPlan(Long userId, PlanCreateRequest request) {

        // 1. 플랜 생성
        Plan plan = new Plan();
        plan.setUserId(userId);
        String regionsStr = "";
        if (request.getRegions() != null && !request.getRegions().isEmpty()) {
            regionsStr = String.join(",", request.getRegions());
        }
        plan.setRegionName(regionsStr);
        plan.setTitle(regionsStr + " 여행");
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : false);

        // 2. 플랜 저장
        planMapper.insertPlan(plan);
        if(plan.getPlanId()==null){
            throw new RuntimeException("플랜 생성 중 오류 발생");
        }

        // 3. 작성자 멤버 추가
        PlanMember newMember = PlanMember.builder()
                .planId(plan.getPlanId())
                .userId(userId)
                .role("OWNER")
                .status("JOINED")
                .joinedAt(LocalDateTime.now())
                .build();
        planMemberMapper.insertPlanMember(newMember);

        return plan.getPlanId();
    }

    @Override
    @Transactional
    public void updatePlan(Long userId, Long planId, PlanUpdateRequest request) {
        PlanMember member = planMemberMapper.findByPlanIdAndUserId(planId, userId);

        // 권한 확인
        if (member == null || !"OWNER".equals(member.getRole())) {
            throw new AccessDeniedException("플랜 수정 권한이 없습니다. (작성자만 가능)");
        }

        // 날짜 예외처리
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (request.getStartDate().isAfter(request.getEndDate())) {
                throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
            }
        }

        Plan plan = new Plan();
        plan.setPlanId(planId);
        plan.setTitle(request.getTitle());
        plan.setRegionList(request.getRegions());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setIsPublic(request.getIsPublic());
        plan.setUpdatedAt(LocalDateTime.now());

        planMapper.updatePlan(plan);
    }

    @Override
    @Transactional
    public void deletePlan(Long userId, Long planId) {
        // 권한 확인
        PlanDetailResponse plan = planMapper.selectPlanDetail(planId);

        if (plan == null) throw new IllegalArgumentException("플랜이 없습니다.");
        if (!plan.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("작성자만 삭제할 수 있습니다.");
        }

        // 소프트 삭제 => 여행기 삭제 로직 필요
        planMapper.deletePlan(planId, userId);
    }

    @Override
    public void updateVisibility(Long planId, Long userId, boolean isPublic) {
        PlanDetailResponse plan = planMapper.selectPlanDetail(planId);
        if(plan == null) throw new IllegalArgumentException("여행 계획이 없습니다.");

        if(!plan.getOwnerId().equals(userId)) throw new SecurityException("공개 설정 변경은 방장만 가능합니다.");
        planMapper.updatePlanVisibility(planId, isPublic);
    }
}