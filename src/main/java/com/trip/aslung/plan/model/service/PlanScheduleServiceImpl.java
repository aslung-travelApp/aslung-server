package com.trip.aslung.plan.model.service;

import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.plan.model.dto.PlanSchedule;
import com.trip.aslung.plan.model.dto.ScheduleMoveRequest;
import com.trip.aslung.plan.model.dto.ScheduleUpdateRequest;
import com.trip.aslung.planMember.model.mapper.PlanMemberMapper;
import com.trip.aslung.plan.model.mapper.PlanScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PlanScheduleServiceImpl implements PlanScheduleService{

    private final PlanScheduleMapper planScheduleMapper;
    private final PlanMemberMapper planMemberMapper;
    @Override
    public void addSchedule(Long userId, Long planId, PlanSchedule request) {
        validatePermission(planId,userId);

        request.setPlanId(planId);
        planScheduleMapper.createSchedule(request);
    }

    @Override
    public void updateSchedule(Long userId, Long planId, Long scheduleId, ScheduleUpdateRequest request) {
        validatePermission(planId,userId);
        log.info("[update] planId : {}, userId : {}, scheduleId : {}", userId, planId, scheduleId);
        log.info("memo: {}, placeId:{}", request.getMemo(), request.getPlaceId());
        PlanSchedule schedule = new PlanSchedule();
        schedule.setScheduleId(scheduleId);
        schedule.setPlanId(planId);
        schedule.setPlaceId(request.getPlaceId());
        schedule.setMemo(request.getMemo());
        planScheduleMapper.updateSchedule(schedule);
    }

    @Override
    public void deleteSchedule(Long userId, Long planId, Long scheduleId) {
        validatePermission(planId,userId);

        // 예외처리
        PlanSchedule schedule = planScheduleMapper.findById(scheduleId);
        if (schedule == null || !schedule.getPlanId().equals(planId)) {
            throw new IllegalArgumentException("존재하지 않는 스케줄입니다.");
        }

        planScheduleMapper.deleteSchedule(scheduleId);

        // 빈자리 메꾸기
        planScheduleMapper.pullScheduleOrders(
                planId,
                schedule.getDayNumber(),
                schedule.getOrderIndex()
        );
    }

    @Override
    public void moveSchedule(Long userId, Long planId, Long scheduleId, ScheduleMoveRequest request) {
        // 1. 권한 체크 및 데이터 가져오기
        validatePermission(planId, userId);
        PlanSchedule schedule = planScheduleMapper.findById(scheduleId);

        if (schedule == null || !schedule.getPlanId().equals(planId)) {
            throw new IllegalArgumentException("잘못된 스케줄입니다.");
        }

        int currentDay = schedule.getDayNumber();
        int currentOrder = schedule.getOrderIndex();

        int newDay = request.getTargetDay();
        int newOrder = request.getTargetOrder();

        // 2. 같은 위치로 이동하는 거면 return
        if (currentDay == newDay && currentOrder == newOrder) return;

        // ==========================================
        // 핵심 로직: 뽑고(Pull) -> 밀고(Push) -> 넣기
        // ==========================================

        int maxOrder = planScheduleMapper.selectMaxOrderIndex(planId, newDay);
        if (newOrder > maxOrder + 1) {
            newOrder = maxOrder + 1;
        }
        log.info("maxOrder: {}, newOrder: {}", maxOrder, newOrder);

        // CASE A: 다른 날짜로 이동할 때
        if (currentDay != newDay) {
            // 1. [원래 날짜] 내 뒤에 있는 애들 앞으로 당기기 (-1)
            planScheduleMapper.pullScheduleOrders(planId, currentDay, currentOrder);

            // 2. [이사 갈 날짜] 들어갈 자리의 뒤에 있는 애들 뒤로 밀기 (+1)
            planScheduleMapper.pushScheduleOrders(planId, newDay, newOrder);
        }
        // CASE B: 같은 날짜 내에서 순서만 바꿀 때
        else {
            if (currentOrder < newOrder) {
                // 뒤로 이동 (예: 1번 -> 3번)
                // : 1번과 3번 사이(2,3)를 앞으로(-1) 당김
                planScheduleMapper.moveOrderBack(planId, currentDay, currentOrder, newOrder);
            } else {
                // 앞으로 이동 (예: 3번 -> 1번)
                // : 1번과 3번 사이(1,2)를 뒤로(+1) 밈
                planScheduleMapper.moveOrderFront(planId, currentDay, currentOrder, newOrder);
            }
        }

        // 3. 위치 확정
        schedule.setDayNumber(newDay);
        schedule.setOrderIndex(newOrder);
        planScheduleMapper.updateScheduleDayAndOrder(schedule);
    }

    private void validatePermission(Long planId, Long userId) {
        PlanMember member = planMemberMapper.findByPlanIdAndUserId(planId, userId);

        // 멤버가 아니거나, 권한이 VIEWER(보기 전용)라면 거절
        if (member == null || "VIEWER".equals(member.getRole())) {
            throw new AccessDeniedException("일정 수정 권한이 없습니다.");
        }
    }
}