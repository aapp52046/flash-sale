package com.flashsale.service;

import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.entity.FlashOrder;
import com.flashsale.repository.FlashOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashOrderService {

    private final FlashOrderRepository flashOrderRepository;

    public FlashOrder getByOrderNo(String orderNo) {
        return flashOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new FlashSaleException(404, "訂單不存在"));
    }

    public List<FlashOrder> getMyOrders(Long userId) {
        return flashOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public boolean hasOrdered(Long userId, Long flashProductId) {
        return flashOrderRepository.existsByUserIdAndFlashProductId(userId, flashProductId);
    }
}
