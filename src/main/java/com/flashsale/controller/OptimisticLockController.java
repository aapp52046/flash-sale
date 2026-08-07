package com.flashsale.controller;

import com.flashsale.dto.response.ApiResponse;
import com.flashsale.service.OptimisticLockDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flash/test")
@RequiredArgsConstructor
public class OptimisticLockController {

    private final OptimisticLockDemoService optimisticLockDemoService;

    @PostMapping("/optimistic-lock")
    public ApiResponse<Map<String, Object>> optimisticLock(@RequestParam Long productId) {
        return ApiResponse.success(optimisticLockDemoService.deductStock(productId));
    }
}
