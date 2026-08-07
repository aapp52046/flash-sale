package com.flashsale.controller;

import com.flashsale.dto.response.ApiResponse;
import com.flashsale.service.LocalLockDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flash/test")
@RequiredArgsConstructor
public class LocalLockController {

    private final LocalLockDemoService localLockDemoService;

    @PostMapping("/local-lock")
    public ApiResponse<Map<String, Object>> localLock(@RequestParam Long productId) {
        return ApiResponse.success(localLockDemoService.deductStock(productId));
    }
}
