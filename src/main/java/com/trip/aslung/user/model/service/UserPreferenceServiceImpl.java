package com.trip.aslung.user.model.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trip.aslung.user.model.dto.PreferenceRequest;
import com.trip.aslung.user.model.dto.UserPreferences;
import com.trip.aslung.user.model.mapper.UserPreferencesMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService{

    private final UserPreferencesMapper userPreferencesMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void savePreference(Long userId, PreferenceRequest requestDto) {
        // 키워드 리스트 -> JSON 문자열 변환
        String keywordsJson = "[]";
        try {
            if (requestDto.getInterestKeywords() != null) {
                keywordsJson = objectMapper.writeValueAsString(requestDto.getInterestKeywords());
            }
        } catch (Exception e) {
            throw new RuntimeException("JSON 변환 오류", e);
        };

        UserPreferences userPreferences = UserPreferences.builder()
                .userId(userId)
                .travelStyle(requestDto.getTravelStyle())
                .pace(requestDto.getPace())
                .interestKeywords(keywordsJson)
                .build();

        if(!userPreferencesMapper.existsByUserId(userId)){
            userPreferencesMapper.insertUserPreferences(userPreferences);
        }
    }
}
