package com.flashsale.service;

import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.entity.Product;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(Product product) {
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new FlashSaleException(404, "商品不存在"));
    }

    public List<Product> getAll() {
        return productRepository.findAll();
    }
}
