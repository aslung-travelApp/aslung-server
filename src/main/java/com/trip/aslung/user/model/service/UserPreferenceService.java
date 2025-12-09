package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.PreferenceRequest;
import com.trip.aslung.user.model.dto.UserPreferences;

public interface UserPreferenceService {
    void savePreference(Long userId, PreferenceRequest requestDto);
}
