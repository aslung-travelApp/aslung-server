package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    User getUserByEmail(String email);
    User findByUserId(Long userId);
    void updateProfile(Long userId, UserUpdateRequest request, MultipartFile image);
    UserStatsResponse getUserStats(Long userId);
    void  signUp(UserSignUpRequest request);
    TokenResponse reissue(String refreshToken);
}