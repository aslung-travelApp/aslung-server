package com.trip.aslung.plan.model.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Plan {
    private Long planId;
    private Long userId;
    private String title;
    private String regionName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPublic;
    private String shareUuid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // DB 문자열 -> List로 변환해서 가져오기
    public List<String> getRegionList() {
        if (this.regionName == null || this.regionName.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(this.regionName.split(","));
    }

    // List -> DB 문자열로 변환해서 세팅하기
    public void setRegionList(List<String> regions) {
        if (regions != null && !regions.isEmpty()) {
            this.regionName = String.join(",", regions);
        } else {
            this.regionName = null;
        }
    }
}