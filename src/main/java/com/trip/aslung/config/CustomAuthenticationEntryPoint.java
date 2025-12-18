package com.trip.aslung.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        // 1. 상태 코드를 401로 설정
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 2. 응답 타입을 JSON으로 설정
        response.setContentType("application/json;charset=UTF-8");
        // 3. JSON 메시지 작성
        response.getWriter().write("{" +
                "\"status\": 401," +
                "\"error\": \"UNAUTHORIZED\"," +
                "\"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인 해주세요.\"" +
                "}");
    }
}