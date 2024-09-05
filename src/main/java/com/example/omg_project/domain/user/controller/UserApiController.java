package com.example.omg_project.domain.user.controller;

import com.example.omg_project.domain.role.entity.Role;
import com.example.omg_project.domain.user.dto.request.UserLoginRequest;
import com.example.omg_project.domain.user.dto.response.UserLoginResponse;
import com.example.omg_project.domain.user.entity.RandomNickname;
import com.example.omg_project.domain.user.entity.User;
import com.example.omg_project.domain.user.service.UserService;
import com.example.omg_project.global.exception.CustomException;
import com.example.omg_project.global.exception.ErrorCode;
import com.example.omg_project.global.jwt.service.RedisBlackTokenService;
import com.example.omg_project.global.jwt.service.RedisRefreshTokenService;
import com.example.omg_project.global.jwt.util.JwtTokenizer;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.omg_project.global.jwt.util.JwtTokenizer.REFRESH_TOKEN_EXPIRE_COUNT;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/users")
public class UserApiController {

    private final JwtTokenizer jwtTokenizer;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final RedisRefreshTokenService redisRefreshTokenService;
    private final RedisBlackTokenService redisBlackTokenService;

    /**
     * 로그인 요청 시 jwt 토큰 발급
     * @param userLoginDto 로그인 dto
     * @param bindingResult 필드 에러 확인
     * @param response 클라이언트 응답 정보
     * @return 응답
     */
    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid UserLoginRequest userLoginDto,
                                BindingResult bindingResult,
                                HttpServletResponse response){

        if(bindingResult.hasErrors()){ // 필드 에러 확인
            return new ResponseEntity("필드 에러 발생", HttpStatus.BAD_REQUEST);
        }

        Optional<User> user = userService.findByUsername(userLoginDto.getUsername());

        if (!user.isPresent()) {
            return new ResponseEntity("아이디가 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }

        // 비밀번호 일치여부 체크
        if(!passwordEncoder.matches(userLoginDto.getPassword(), user.get().getPassword())) {
            return new ResponseEntity("비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        List<String> roles = user.get().getRoles().stream().map(Role::getName).collect(Collectors.toList());

        redisRefreshTokenService.deleteRefreshToken(String.valueOf(user.get().getId()));

        // 토큰 발급
        String accessToken = jwtTokenizer.createAccessToken(user.get().getId(), user.get().getUsername(), user.get().getName(),  roles);
        String refreshToken = jwtTokenizer.createRefreshToken(user.get().getId(), user.get().getUsername(), user.get().getName(), roles);

        // 레디스 토큰 저장
        redisRefreshTokenService.addRefreshToken(refreshToken, jwtTokenizer.REFRESH_TOKEN_EXPIRE_COUNT);

        // 토큰 쿠키 저장
        Cookie accessTokenCookie = new Cookie("accessToken",accessToken);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(Math.toIntExact(jwtTokenizer.ACCESS_TOKEN_EXPIRE_COUNT/1000));

        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(Math.toIntExact(REFRESH_TOKEN_EXPIRE_COUNT/1000));

        response.addCookie(accessTokenCookie);
        response.addCookie(refreshTokenCookie);

        // 응답 값
        UserLoginResponse loginResponseDto = UserLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.get().getId())
                .username(user.get().getUsername())
                .build();

        return new ResponseEntity(loginResponseDto, HttpStatus.OK);
    }

    /**
     * 로그아웃 요청
     * @param accessToken 엑세스토큰
     * @param refreshToken 리프레시토큰
     * @param response 클라이언트 응답 정보
     */
    @GetMapping("/logout")
    public void logout(@CookieValue(name = "accessToken", required = false) String accessToken,
                       @CookieValue(name = "refreshToken", required = false) String refreshToken,
                       HttpServletResponse response) {
        if (accessToken == null) {

            try {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Access token not found in cookies.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        String jwt = accessToken;

        Date expirationTime = Jwts.parser()
                .setSigningKey(jwtTokenizer.getAccessSecret())
                .parseClaimsJws(jwt)
                .getBody()
                .getExpiration();

        // 블랙리스트에 추가
        redisBlackTokenService.addBlacklistedToken(accessToken, expirationTime.getTime() - System.currentTimeMillis());

        SecurityContextHolder.clearContext();

        // 쿠키 삭제
        Cookie accessCookie = new Cookie("accessToken", null);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);
        response.addCookie(accessCookie);

        Cookie refresCcookie = new Cookie("refreshToken", null);
        refresCcookie.setPath("/");
        refresCcookie.setMaxAge(0);
        response.addCookie(refresCcookie);

        // 레디스에서 리프레시 토큰 삭제
        if (refreshToken != null) {
            redisRefreshTokenService.deleteRefreshToken(refreshToken);
        }

        try {
            response.sendRedirect("/");
        } catch (IOException e) {
            throw new CustomException(ErrorCode.TOKEN_DELETION_ERROR);
        }
    }

    /**
     * 랜덤 닉네임 생성
     * @return 랜덤 닉네임 생성
     */
    @GetMapping("/randomNickname")
    public String getRandomNickname() {
        return RandomNickname.generateRandomNickname();
    }

    /**
     * 회원 탈퇴
     * @param userId 사용자 아이디
     * @param accessToken 엑세스토큰
     * @param refreshToken 리프레시토큰
     * @param response 클라이언트 응답 정보
     * @return 응답
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable("userId") Long userId,
                                             @CookieValue(name = "accessToken", required = false) String accessToken,
                                             @CookieValue(name = "refreshToken", required = false) String refreshToken,
                                             HttpServletResponse response) {
        try {
            userService.deleteUser(userId);

            // 로그아웃 로직
            if (accessToken != null) {
                // JWT 토큰 추출
                String jwt = accessToken;

                // 토큰의 만료 시간 추출
                Date expirationTime = Jwts.parser()
                        .setSigningKey(jwtTokenizer.getAccessSecret())
                        .parseClaimsJws(jwt)
                        .getBody()
                        .getExpiration();

                // 레디스 토큰 저장
                redisBlackTokenService.addBlacklistedToken(accessToken, expirationTime.getTime() - System.currentTimeMillis());
            }

            // SecurityContext를 클리어하여 현재 세션을 무효화
            SecurityContextHolder.clearContext();

            // accessToken 쿠키 삭제
            Cookie accessCookie = new Cookie("accessToken", null);
            accessCookie.setPath("/");
            accessCookie.setMaxAge(0);
            response.addCookie(accessCookie);

            // refreshToken 쿠키 삭제
            Cookie refreshCookie = new Cookie("refreshToken", null);
            refreshCookie.setPath("/");
            refreshCookie.setMaxAge(0);
            response.addCookie(refreshCookie);

            // 로그아웃 전 레디스에 저장되어있는 refreshToken 삭제
            if (refreshToken != null) {
                redisRefreshTokenService.deleteRefreshToken(refreshToken);
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            throw new CustomException(ErrorCode.USER_DELETION_ERROR);
        }
    }
}