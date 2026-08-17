package com.flashsale.service;

import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.entity.FlashOrder;
import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.entity.Product;
import com.flashsale.repository.FlashOrderRepository;
import com.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlashOrderService {

    private final FlashOrderRepository flashOrderRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final ProductRepository productRepository;

    public FlashOrder getByOrderNo(String orderNo) {
        FlashOrder order = flashOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new FlashSaleException(404, "訂單不存在"));
        enrichProductNames(List.of(order));
        return order;
    }

    public FlashOrder getAccessibleOrder(String orderNo, Long userId, boolean admin) {
        FlashOrder order = getByOrderNo(orderNo);
        if (!admin && !order.getUserId().equals(userId)) {
            throw new FlashSaleException(404, "訂單不存在");
        }
        return order;
    }

    public List<FlashOrder> getMyOrders(Long userId) {
        List<FlashOrder> orders = flashOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        enrichProductNames(orders);
        return orders;
    }

    public boolean hasOrdered(Long userId, Long flashProductId) {
        return flashOrderRepository.existsByUserIdAndFlashProductId(userId, flashProductId);
    }

    private void enrichProductNames(List<FlashOrder> orders) {
        if (orders.isEmpty()) {
            return;
        }
        Set<Long> flashIds = orders.stream()
                .map(FlashOrder::getFlashProductId)
                .collect(Collectors.toSet());
        Map<Long, Long> flashToProduct = flashSaleProductRepository.findAllById(flashIds).stream()
                .collect(Collectors.toMap(FlashSaleProduct::getId, FlashSaleProduct::getProductId));
        Map<Long, String> names = productRepository.findAllById(new HashSet<>(flashToProduct.values())).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        orders.forEach(order -> {
            Long productId = flashToProduct.get(order.getFlashProductId());
            if (productId == null) {
                order.setProductName("商品 #" + order.getFlashProductId());
            } else {
                order.setProductName(names.getOrDefault(productId, "商品 #" + productId));
            }
        });
    }
}
