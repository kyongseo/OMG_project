package com.example.omg_project.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginResponseDto {

    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
}