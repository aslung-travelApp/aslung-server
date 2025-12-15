package com.trip.aslung.planMember.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {
    private Long planId;        // 수락할 때 필요
    private String planTitle;   // 화면 표시용
    private String ownerName;   // 누가 초대했는지 (방장 닉네임)
    private LocalDateTime invitedAt; // 언제 초대왔는지
}