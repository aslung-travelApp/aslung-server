package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.User;
import com.trip.aslung.user.model.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    @Override
    public User getUserByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public User findByUserId(Long userId) {
        return userMapper.findByUserId(userId);
    }
}