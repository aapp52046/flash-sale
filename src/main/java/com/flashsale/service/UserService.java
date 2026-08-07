package com.flashsale.service;

import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.dto.request.LoginRequest;
import com.flashsale.dto.request.RegisterRequest;
import com.flashsale.dto.response.ApiResponse;
import com.flashsale.entity.User;
import com.flashsale.repository.UserRepository;
import com.flashsale.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public ApiResponse<Map<String, String>> register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new FlashSaleException(400, "帳號已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ApiResponse.success("註冊成功", Map.of("token", token, "username", user.getUsername()));
    }

    public ApiResponse<Map<String, String>> login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new FlashSaleException(401, "帳號或密碼錯誤"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new FlashSaleException(401, "帳號或密碼錯誤");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return ApiResponse.success("登入成功", Map.of("token", token, "username", user.getUsername()));
    }
}
