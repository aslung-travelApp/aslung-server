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
public class User {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String oauthProvider;
    private String oauthId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public void updateProfile(String nickname, String profileImageUrl){
        if(nickname!=null && !nickname.isBlank()) this.nickname = nickname;
        if(profileImageUrl!=null) this.profileImageUrl = profileImageUrl;
    }
}
