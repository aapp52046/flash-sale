package com.flashsale.controller;

import com.flashsale.dto.response.ApiResponse;
import com.flashsale.service.PessimisticLockDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/flash/test")
@RequiredArgsConstructor
public class PessimisticLockController {

    private final PessimisticLockDemoService pessimisticLockDemoService;

    @PostMapping("/pessimistic-lock")
    public ApiResponse<Map<String, Object>> pessimisticLock(@RequestParam Long productId) {
        return ApiResponse.success(pessimisticLockDemoService.deductStock(productId));
    }
}
