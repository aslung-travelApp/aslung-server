package com.trip.aslung.user.model.dto;

import lombok.Data;

@Data
public class UserLoginRequest {
    private String email;
    private String password;
}