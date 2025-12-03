package com.trip.aslung.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {
    private Long prefId;
    private Long userId;
    private String travelStyle;
    private String pace;
    private String interestKeywords;
    private LocalDateTime updatedAt;
}
