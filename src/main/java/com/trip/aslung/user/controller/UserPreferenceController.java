package com.trip.aslung.user.controller;

import com.trip.aslung.user.model.dto.PreferenceRequest;
import com.trip.aslung.user.model.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @PostMapping
    public ResponseEntity<String> savePreferences(
            @AuthenticationPrincipal Long userId,
            @RequestBody PreferenceRequest requestDto){
        userPreferenceService.savePreference(userId, requestDto);

        return ResponseEntity.ok("선호도 조사 저장 완료");
    }
}
