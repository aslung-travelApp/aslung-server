package com.trip.aslung.plan.model.dto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
public class ScheduleUpdateRequest {
    private Long placeId;
    private String memo;
}