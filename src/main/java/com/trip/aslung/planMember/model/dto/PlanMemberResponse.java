package com.trip.aslung.planMember.model.dto;

import lombok.Data;

@Data
public class PlanMemberResponse {
    private Long memberId;      // plan_members.member_id
    private Long planId;        // plan_members.plan_id
    private Long userId;        // plan_members.user_id

    // User 정보 (JOIN으로 가져옴)
    private String nickname;
    private String profileImageUrl;
    private String email;

    // 멤버 상태 정보
    private String role;        // OWNER, EDITOR, VIEWER
    private String status;      // INVITED, JOINED
}