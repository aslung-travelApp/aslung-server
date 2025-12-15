package com.trip.aslung.user.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchResponse {
    private Long userId;
    private String email;
    private String nickname;
    private String profileImageUrl;
}