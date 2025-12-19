package com.trip.aslung.user.model.mapper;

import com.trip.aslung.user.model.dto.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    void insertUser(User user);
    User findByEmail(String email);
    void updateUser(User user);
    User findByUserId(Long userId);
}