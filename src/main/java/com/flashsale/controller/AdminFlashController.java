package com.flashsale.controller;

import com.flashsale.dto.request.FlashProductRequest;
import com.flashsale.dto.response.ApiResponse;
import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.service.FlashProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/flash/products")
@RequiredArgsConstructor
public class AdminFlashController {

    private final FlashProductService flashProductService;

    @PostMapping
    public ApiResponse<FlashSaleProduct> create(@Valid @RequestBody FlashProductRequest request) {
        return ApiResponse.success(flashProductService.create(request));
    }

    @GetMapping
    public ApiResponse<List<FlashSaleProduct>> list() {
        return ApiResponse.success(flashProductService.getAll());
    }

    @PostMapping("/{id}/preheat")
    public ApiResponse<String> preheat(@PathVariable Long id) {
        flashProductService.preheatStock(id);
        return ApiResponse.success("預熱成功");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<String> updateStatus(@PathVariable Long id, @RequestParam int status) {
        flashProductService.updateStatus(id, status);
        return ApiResponse.success("狀態更新成功");
    }
}
