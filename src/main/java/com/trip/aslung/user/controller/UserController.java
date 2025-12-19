package com.trip.aslung.user.controller;

import com.trip.aslung.user.model.dto.*;
import com.trip.aslung.user.model.mapper.UserMapper;
import com.trip.aslung.user.model.service.UserService;
import com.trip.aslung.util.CookieUtil;
import com.trip.aslung.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.Cookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.logout-redirect-uri}")
    private String logoutRedirectUri;

    @GetMapping("/me")
    public ResponseEntity<User> getMyInfo(@AuthenticationPrincipal Long userId){
        log.info("내 정보 조회 요청 : {}", userId);

        User user = userService.findByUserId(userId);

        if(user != null){
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> updateMyInfo(@AuthenticationPrincipal Long userId,
                                             @RequestPart(value="request", required = false) UserUpdateRequest request,
                                             @RequestPart(value="image", required = false)MultipartFile image){
        userService.updateProfile(userId, request, image);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody UserSignUpRequest request){
        userService.signUp(request);
        return ResponseEntity.ok("회원가입이 성공적으로 완료되었습니다.");
    }
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {

        // 1. 서버 세션 및 인증 정보 삭제 (Spring Security)
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());

        // 2. JSESSIONID 등 쿠키 삭제 (안전을 위해)
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        response.addCookie(cookie);

        // 3. 카카오 로그아웃 URL 생성
        String kakaoLogoutUrl = "https://kauth.kakao.com/oauth/logout"
                + "?client_id=" + kakaoClientId
                + "&logout_redirect_uri=" + logoutRedirectUri;

        // 4. 프론트로 URL 반환
        Map<String, String> result = new HashMap<>();
        result.put("logoutUrl", kakaoLogoutUrl);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody UserLoginRequest request, HttpServletResponse response){

        // 1. 이메일 유저 조회
        User user = userMapper.findByEmail(request.getEmail());
        if(user==null){
            throw new RuntimeException("가입되지 않은 이메일입니다.");
        }

        // 2. 비밀번호 검증
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 토큰 발급
        String accessToken = jwtUtil.createAccessToken(user.getUserId());
        String refreshToken = jwtUtil.createRefreshToken(user.getUserId());

        redisTemplate.opsForValue().set(
            "RT: " + user.getUserId(),
            refreshToken,
                 7,
                TimeUnit.DAYS
        );

        // 4. 리프레시 토큰 쿠키에 저장
        cookieUtil.addCookie(response, "refresh_token", refreshToken, 7 * 24 * 60 * 60);

        TokenResponse tokens = TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken("null")
                .build();
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getUserStats(
            @AuthenticationPrincipal Long userId
    ) {
        log.info("통계 조회 요청 - UserID: {}", userId);

        UserStatsResponse stats = userService.getUserStats(userId);

        return ResponseEntity.ok(stats);
    }
    @GetMapping("/search")
    public ResponseEntity<UserSearchResponse> searchUser(
            @AuthenticationPrincipal Long userId,
            @RequestParam String email
    ){
        User user = userService.getUserByEmail(email);

        if(user==null || user.getDeletedAt()!=null) return ResponseEntity.notFound().build();

        UserSearchResponse response = UserSearchResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(
            @CookieValue(value = "refresh_token", required = false) String refreshToken
    ){
        if(refreshToken == null){
            throw new IllegalArgumentException("Refresh Toekn이 쿠키에 없습니다");
        }

        TokenResponse tokens = userService.reissue(refreshToken);
        return ResponseEntity.ok(tokens);
    }
}