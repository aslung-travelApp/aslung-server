package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.*;
import com.trip.aslung.user.model.mapper.UserMapper;
import com.trip.aslung.util.JWTUtil;
import com.trip.aslung.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final S3Uploader s3Uploader;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

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

    @Override
    public UserStatsResponse getUserStats(Long userId) {
        int tripCount = 0;
        int reviewCount = 0;
        int placeLikeCount = 0;

        return new UserStatsResponse(tripCount, reviewCount, placeLikeCount);
    }

    @Override
    public void signUp(UserSignUpRequest request) {
        // 1. 이메일 중복 체크
        User existingUser = userMapper.findByEmail(request.getEmail());
        if (existingUser != null) {
            String provider = existingUser.getOauthProvider();
            String message;

            if ("KAKAO".equals(provider)) {
                message = "이미 [카카오]로 가입된 이메일입니다.\n카카오 로그인을 이용해주세요.";
            } else {
                message = "이미 가입된 이메일입니다.\n로그인을 진행해주세요.";
            }

            throw new RuntimeException(message);
        }

        // 2. 비밀번호 암호화
        String encryptedPass = passwordEncoder.encode(request.getPassword());

        // 3. User 객체 생성
        User user = User.builder()
                .email(request.getEmail())
                .password(encryptedPass)
                .nickname(request.getNickname())
                .profileImageUrl(request.getProfileImageUrl())
                .oauthProvider("LOCAL")
                .build();

        userMapper.insertUser(user);
    }

    @Override
    public TokenResponse reissue(String refreshToken) {

        // 1. refresh Token 유효성 검사
        if(!jwtUtil.validateToken(refreshToken)){
            throw new RuntimeException("Refresh Token 유효하지 않습니다.");
        }

        // 2. Token에서 userId 추출
        Long userId = jwtUtil.getUserId(refreshToken);

        // 3. Redis에서 해당 유저 Refresh Token 가져오기
        String redisRefreshToken = (String) redisTemplate.opsForValue().get("RT:" + userId);

        if(redisRefreshToken==null || !redisRefreshToken.equals(refreshToken)){
            throw new RuntimeException("Refresh Token 정보 일치하지 않거나 만료되었습니다");
        }

        // 4. 새로운 Access Token 발급
        String newAccessToken = jwtUtil.createAccessToken(userId);

        // 5. Refresh Token도 갱신
        String newRefreshToken = jwtUtil.createRefreshToken(userId);
        redisTemplate.opsForValue().set("RT:" + userId, newRefreshToken, 7, TimeUnit.DAYS);
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}