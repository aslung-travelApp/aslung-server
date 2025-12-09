package com.trip.aslung.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserStatsResponse {
    private int tripCount;
    private int reviewCount;
    private int LikeCount;
}