package com.flashsale.controller;

import com.flashsale.dto.response.ApiResponse;
import com.flashsale.service.RedisLockDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flash/test")
@RequiredArgsConstructor
public class RedisLockController {

    private final RedisLockDemoService redisLockDemoService;

    @PostMapping("/redis-lock")
    public ApiResponse<Map<String, Object>> redisLock(@RequestParam Long productId) {
        return ApiResponse.success(redisLockDemoService.deductStock(productId));
    }
}
