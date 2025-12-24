package com.trip.aslung.plan.model.mapper;

import com.trip.aslung.plan.model.dto.Place;
import com.trip.aslung.plan.model.dto.PlanSchedule;
import com.trip.aslung.plan.model.dto.ScheduleAddRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlanScheduleMapper {

    PlanSchedule findById(Long scheduleId);
    void createSchedule(ScheduleAddRequest planSchedule);
    void updateSchedule(PlanSchedule planSchedule);
    void deleteSchedule(Long scheduleId);
    void pullScheduleOrders(Long planId, int dayNumber, int startOrder);
    void pushScheduleOrders(Long planId, int dayNumber, int startOrder);
    void moveOrderBack(Long planId, int day, int oldOrder, int newOrder);
    void moveOrderFront(Long planId, int day, int oldOrder, int newOrder);
    void updateScheduleDayAndOrder(PlanSchedule schedule);
    int selectMaxOrderIndex(Long planId, int dayNumber);
    List<PlanSchedule> selectSchedulesByPlanId(Long planId);
    void insertSchedule(PlanSchedule planSchedule);
}
