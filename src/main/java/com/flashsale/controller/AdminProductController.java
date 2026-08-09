package com.flashsale.controller;

import com.flashsale.dto.response.ApiResponse;
import com.flashsale.entity.Product;
import com.flashsale.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.success(productService.getAll());
    }
}