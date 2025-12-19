package com.trip.aslung.user.model.dto;

import lombok.Data;

@Data
public class UserReissueRequest {
    private String accessToken;
    private String refreshToken;
}