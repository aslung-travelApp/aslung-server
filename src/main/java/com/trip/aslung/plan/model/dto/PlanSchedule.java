package com.trip.aslung.plan.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PlanSchedule {
    private Long scheduleId;
    private Long planId;
    private Long placeId;       // 장소 ID
    private int dayNumber;      // 1일차, 2일차..
    private int orderIndex;     // 순서
    private String memo;        // 메모

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // DB 테이블에는 없지만, JOIN해서 가져온 데이터를 담을 용도
    private String placeName;   // places 테이블의 name
    private String category;    // places 테이블의 category
}