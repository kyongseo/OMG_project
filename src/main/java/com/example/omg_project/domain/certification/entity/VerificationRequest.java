package com.example.omg_project.domain.certification.entity;

import lombok.Getter;
import lombok.Setter;

// 이메일 인증 코드
@Getter @Setter
public class VerificationRequest {

    private String mail;
    private int code;
}
