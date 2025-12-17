package com.trip.aslung.plan.model.service;

import com.trip.aslung.plan.model.dto.PlanSchedule;
import com.trip.aslung.plan.model.dto.ScheduleMoveRequest;
import com.trip.aslung.plan.model.dto.ScheduleUpdateRequest;

import java.util.List;

public interface PlanScheduleService {
    void addSchedule(Long userId, Long planId, PlanSchedule request);
    void updateSchedule(Long userId, Long planId, Long scheduleId, ScheduleUpdateRequest request);
    void deleteSchedule(Long userId, Long planId, Long scheduleId);
    void moveSchedule(Long userId, Long planId, Long scheduleId, ScheduleMoveRequest request);
    List<PlanSchedule> getSchedulesByPlanId(Long planId);
}