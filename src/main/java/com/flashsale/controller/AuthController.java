
package com.flashsale.controller;

import com.flashsale.dto.request.LoginRequest;
import com.flashsale.dto.request.RegisterRequest;
import com.flashsale.dto.response.ApiResponse;
import com.flashsale.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@Valid @RequestBody RegisterRequest request,
                                                      HttpServletResponse response) {
        ApiResponse<Map<String, String>> result = userService.register(request);
        setJwtCookie(response, result.getData().get("token"));
        return result;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletResponse response) {
        ApiResponse<Map<String, String>> result = userService.login(request);
        setJwtCookie(response, result.getData().get("token"));
        return result;
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);
    }
}
