package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.User;
import com.trip.aslung.user.model.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserMapper userMapper;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 유저 정보 가져오기 (Spring이 처리)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();

        Map<String, Object> kakaoAccount = (Map<String,Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        String email = (String) kakaoAccount.get("email");
        String nickname = (String) profile.get("nickname");
        String profileImage = (String) profile.get("profile_image_url");
        String oauthId = String.valueOf(attributes.get("id"));

        log.info("카카오 로그인 요청 : email={}, oauthId={}", email, oauthId);

        User savedUser = saveOrUpdate(email, nickname, profileImage, oauthId);

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "id"
        );
    }

    private User saveOrUpdate(String email, String nickname, String profileImage, String oauthId){
        User user = userMapper.findByEmail(email);

        if(user == null){
            // 신규 유저 - 회원가입
            user = User.builder()
                    .email(email)
                    .nickname(nickname)
                    .profileImageUrl(profileImage)
                    .oauthProvider("KAKAO")
                    .oauthId(oauthId)
                    .build();
            userMapper.insertUser(user);
            log.info("신규 회원가입 완료 : {}", email);
        }

        return user;
    }
}
