package com.trip.aslung.planMember.controller;

import com.trip.aslung.planMember.model.dto.InviteRequest;
import com.trip.aslung.planMember.model.dto.PlanMember;
import com.trip.aslung.planMember.model.dto.PlanMemberResponse;
import com.trip.aslung.planMember.model.service.PlanMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@Slf4j
@RequestMapping("/api/v1/plans/{planId}/members")
@RequiredArgsConstructor
public class PlanMemberController {

    private final PlanMemberService planMemberService;

    @GetMapping
    public ResponseEntity<List<PlanMemberResponse>> getMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId
    ){
        List<PlanMemberResponse> members = planMemberService.getMember(userId, planId);
        return ResponseEntity.ok(members);
    }
    @PostMapping
    public ResponseEntity<String> inviteMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @RequestBody InviteRequest request
    ){
        planMemberService.inviteMember(planId,userId, request.getTargetUserId());
        return ResponseEntity.ok("초대 완료");
    }

    @PatchMapping("/accept")
    public ResponseEntity<String> acceptInvitation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId
    ){
        planMemberService.acceptInvitation(planId, userId);
        return ResponseEntity.ok("여행 초대 수락");
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<String> kikMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId,
            @PathVariable Long memberId
    ){
        planMemberService.kickMember(planId,userId,memberId);
        return ResponseEntity.ok("멤버 내보내기");
    }

    @DeleteMapping("/me")
    public ResponseEntity<String> leavePlan(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long planId
    ){
        planMemberService.leavePlan(planId,userId);
        return ResponseEntity.ok("멤버 나가기");
    }
}
