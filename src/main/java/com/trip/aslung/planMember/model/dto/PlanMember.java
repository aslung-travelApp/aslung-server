package com.trip.aslung.planMember.model.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanMember {
    private Long memberId;
    private Long planId;
    private Long userId;

    private String role;        // OWNER, EDITOR, VIEWER
    private String status;      // INVITED, JOINED

    private LocalDateTime createdAt;
    private LocalDateTime joinedAt;
    private LocalDateTime updatedAt;
}