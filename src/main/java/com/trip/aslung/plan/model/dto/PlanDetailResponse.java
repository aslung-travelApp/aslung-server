package com.trip.aslung.plan.model.dto;

import com.trip.aslung.planMember.model.dto.PlanMember;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class PlanDetailResponse {
    // 1. 기본 정보
    private Long planId;
    private String title;
    private String regionName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPublic;
    private Long ownerId;

    // 2. 멤버 목록
    private List<PlanMember> members;

    // 3. 일정 목록
    private List<PlanSchedule> schedules;
}