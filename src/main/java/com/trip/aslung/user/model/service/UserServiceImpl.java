package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.User;
import com.trip.aslung.user.model.dto.UserUpdateRequest;
import com.trip.aslung.user.model.mapper.UserMapper;
import com.trip.aslung.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final S3Uploader s3Uploader;
    @Override
    public User getUserByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public User findByUserId(Long userId) {
        return userMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, UserUpdateRequest request, MultipartFile image) {
        User user = userMapper.findByUserId(userId);

        String profileImageUrl = user.getProfileImageUrl();

        if(image != null && !image.isEmpty()){
            profileImageUrl = s3Uploader.upload(image, "profile");
        }

        user.updateProfile(request.getNickname(), profileImageUrl);

        userMapper.updateUser(user);
    }
}