package com.trip.aslung.user.model.mapper;

import com.trip.aslung.user.model.dto.UserPreferences;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferencesMapper {
    boolean existsByUserId(Long userId);
    void insertUserPreferences(UserPreferences userPreferences);
}
