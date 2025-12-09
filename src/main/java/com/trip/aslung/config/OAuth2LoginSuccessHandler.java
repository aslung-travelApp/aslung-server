package com.trip.aslung.config;

import com.trip.aslung.user.model.dto.User;
import com.trip.aslung.user.model.mapper.UserMapper;
import com.trip.aslung.user.model.mapper.UserPreferencesMapper;
import com.trip.aslung.util.JWTUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil;
    private final UserMapper userMapper;
    private final UserPreferencesMapper userPreferencesMapper;

    @Value("${app.front-url}")
    private String frontUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 1. 카카오 인증 정보에서 이메일 추출
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> kakaoAccount = (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
        String email = (String) kakaoAccount.get("email");

        log.info("OAuth2 Login 성공, 사용자 : {}", email);

        // 2. DB에서 유저 조회
        User user = userMapper.findByEmail(email);

        // 3. userId로 Access Token 발급
        String accessToken = jwtUtil.createAccessToken(user.getUserId());
        log.info("⭐⭐⭐ [Postman 테스트용 토큰] : {}", accessToken);

        // 4. 토큰 쿠키에 담기
        createCookie(response, "Authorization", accessToken);

        // 5. 새로운 회원이면 선호도 조사 페이지로 넘기기
        boolean hasPreferences = false;
        if(user != null){
            hasPreferences = userPreferencesMapper.existsByUserId(user.getUserId());
        }

        String targetUrl;
        if(hasPreferences){
            log.info("기존 회원 -> 메인 페이지 이동");
            targetUrl = UriComponentsBuilder.fromUriString(frontUrl)
                    .build().toUriString();
        } else {
            log.info("신규/미조사 회원 -> 설문조사 페이지 이동");
            targetUrl = UriComponentsBuilder.fromUriString(frontUrl)
                    .path("/survey")
                    .queryParam("new", "true")
                    .build().toUriString();
        }
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void createCookie(HttpServletResponse response, String key, String value){
        Cookie cookie = new Cookie(key, value);
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        cookie.setMaxAge(60*60); // 1시간(토큰 만료시간)
        //cookie.setSecure(true); // 배포시 true

        response.addCookie(cookie);
    }
}