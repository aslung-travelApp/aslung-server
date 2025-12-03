package com.trip.aslung.config;

import com.trip.aslung.util.JWTUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");

        if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        if(jwtUtil.validateToken(token)){
            String email = jwtUtil.getEmail(token);

            log.info("유효한 토큰 감지 : 사용자 이메일 = {}", email);

            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContextHolder.getContext().setAuthentication(authToken);
        } else {
            log.warn("유효하지 않은 토큰입니다.");
        }

        filterChain.doFilter(request, response);
    }
}
