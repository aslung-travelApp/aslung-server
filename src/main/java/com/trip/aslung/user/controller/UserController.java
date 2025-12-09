package com.trip.aslung.user.controller;

import com.trip.aslung.user.model.dto.User;
import com.trip.aslung.user.model.dto.UserStatsResponse;
import com.trip.aslung.user.model.dto.UserUpdateRequest;
import com.trip.aslung.user.model.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo(@AuthenticationPrincipal Long userId){
        log.info("내 정보 조회 요청 : {}", userId);

        User user = userService.findByUserId(userId);

        if(user != null){
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> updateMyInfo(@AuthenticationPrincipal Long userId,
                                             @RequestPart(value="request", required = false) UserUpdateRequest request,
                                             @RequestPart(value="image", required = false)MultipartFile image){
        userService.updateProfile(userId, request, image);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // 현재 프론트에서 액세스 토큰 버리기로 구현
        // refresh token 구현 후 삭제 로직 필요
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @AuthenticationPrincipal Long userId
    ) {
        log.info("통계 조회 요청 - UserID: {}", userId);

        UserStatsResponse stats = userService.getUserStats(userId);

        return ResponseEntity.ok(stats);
    }

}