package com.flashsale.controller;

import com.flashsale.dto.request.FlashOrderRequest;
import com.flashsale.dto.response.ApiResponse;
import com.flashsale.entity.FlashOrder;
import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.security.SecurityUtil;
import com.flashsale.service.FlashOrderService;
import com.flashsale.service.FlashProductService;
import com.flashsale.service.FlashSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;
    private final FlashProductService flashProductService;
    private final FlashOrderService flashOrderService;
    private final SecurityUtil securityUtil;

    @GetMapping("/products")
    public ApiResponse<List<FlashSaleProduct>> activeProducts() {
        return ApiResponse.success(flashProductService.getActiveFlashProducts());
    }

    @GetMapping("/products/{id}")
    public ApiResponse<FlashSaleProduct> productDetail(@PathVariable Long id) {
        return ApiResponse.success(flashProductService.getById(id));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> placeOrder(@Valid @RequestBody FlashOrderRequest request) {
        Long userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "請先登入");
        }
        Map<String, Object> result = flashSaleService.executeSeckill(userId, request.getFlashProductId());
        return ApiResponse.<Map<String, Object>>builder()
                .code((int) result.get("code"))
                .message((String) result.get("message"))
                .data(result.containsKey("data") ? (Map<String, Object>) result.get("data") : null)
                .build();
    }

    @GetMapping("/orders/my")
    public ApiResponse<List<FlashOrder>> myOrders() {
        Long userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "請先登入");
        }
        return ApiResponse.success(flashOrderService.getMyOrders(userId));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<FlashOrder> orderDetail(@PathVariable String orderNo) {
        Long userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(401, "請先登入");
        }
        return ApiResponse.success(
                flashOrderService.getAccessibleOrder(orderNo, userId, securityUtil.isAdmin()));
    }
}
