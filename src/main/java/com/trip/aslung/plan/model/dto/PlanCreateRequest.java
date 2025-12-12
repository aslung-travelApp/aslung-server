package com.trip.aslung.plan.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
@NoArgsConstructor
public class PlanCreateRequest {
    private List<String> regions;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPublic;   // 공개 여부 (선택 사항, 안 보내면 false)
}