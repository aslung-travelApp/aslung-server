package com.trip.aslung.plan.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PlanVisibilityRequest {
    @JsonProperty("isPublic")
    private boolean isPublic; // true: 공개, false: 비공개
}