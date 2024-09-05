package com.example.omg_project.global.oauth2.service;

import com.example.omg_project.domain.role.entity.Role;
import com.example.omg_project.domain.role.repository.RoleRepository;
import com.example.omg_project.domain.user.entity.User;
import com.example.omg_project.domain.user.repository.UserRepository;
import com.example.omg_project.global.exception.CustomException;
import com.example.omg_project.global.exception.ErrorCode;
import com.example.omg_project.global.oauth2.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOauth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response oAuth2Response = null;

        switch (registrationId) {
            case "naver":
                oAuth2Response = new NaverResponse(oAuth2User.getAttributes());
                break;
            case "kakao":
                oAuth2Response = new KakaoResponse(oAuth2User.getAttributes());
                break;
            case "google":
                oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
                break;
            default:
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        String provider = oAuth2Response.getProvider();
        String providerId = oAuth2Response.getProviderId();
        String username = provider + " " + providerId; // 유저명 생성 (OAuth2 공급자명 + 공급자ID)

        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isPresent()) {
            return new CustomOAuth2User(oAuth2Response, userOptional.get().getRoles().stream().map(Role::getName).collect(Collectors.joining(",")));
        }

        // 새로운 유저에 대한 처리
        String roleName = "ROLE_USER";
        Optional<Role> roleOptional = roleRepository.findByName(roleName);
        Role role;
        if (roleOptional.isEmpty()) {
            role = new Role(roleName);
            role = roleRepository.save(role);
        } else {
            role = roleOptional.get();
        }

        User newUser = User.builder()
                .name(oAuth2Response.getName())
                .username(username)
                .roles(Set.of(role))
                .providerId(oAuth2Response.getProviderId())
                .provider(oAuth2Response.getProvider())
                .password("")
                // 로그인 후 추가정보 기입
                .phoneNumber("01000000000")
                .birthdate(LocalDate.from(LocalDateTime.now()))
                .gender("default")
                .registrationDate(LocalDateTime.now())
                .usernick(oAuth2Response.getEmail())
                .status("ACTIVE")
                .build();
        userRepository.save(newUser);

        return new CustomOAuth2User(oAuth2Response, roleName);
    }
}