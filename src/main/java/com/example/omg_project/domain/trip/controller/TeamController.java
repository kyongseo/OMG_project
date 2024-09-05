package com.example.omg_project.domain.trip.controller;

import com.example.omg_project.domain.user.entity.User;
import com.example.omg_project.domain.user.service.UserService;
import com.example.omg_project.global.jwt.util.JwtTokenizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {
    private final JwtTokenizer jwtTokenizer;
    private final UserService userService;

    /**
     * 팀 가입 폼
     */
    @GetMapping("/join")
    public String showJoinTeamForm(Model model, HttpServletRequest request) {
        String accessToken = jwtTokenizer.getAccessTokenFromCookies(request);
        String username = jwtTokenizer.getUsernameFromToken(accessToken);
        User user = userService.findByUsername(username).orElseThrow();
        model.addAttribute("user", user);
        return "team/join";
    }

    /**
     * 현재 사용자가 가입한 팀 목록
     */
    @GetMapping("/myteam")
    public String showTeamsPage(Model model, HttpServletRequest request) {
        String accessToken = jwtTokenizer.getAccessTokenFromCookies(request);
        String username = jwtTokenizer.getUsernameFromToken(accessToken);
        User user = userService.findByUsername(username).orElseThrow();
        model.addAttribute("user", user);
        return "team/myteam";
    }
}

