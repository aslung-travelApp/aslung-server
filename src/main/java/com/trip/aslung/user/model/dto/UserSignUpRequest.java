package com.trip.aslung.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSignUpRequest {
    private String email;
    private String password;
    private String nickname;
    private String  profileImageUrl;
}