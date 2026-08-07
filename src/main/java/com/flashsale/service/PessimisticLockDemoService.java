package com.flashsale.service;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Phase 7 — Pessimistic lock via {@code SELECT ... FOR UPDATE}.
 * <p>
 * Row-level lock held until transaction commit. Highest safety, higher latency
 * under contention. Used as fallback when optimistic retries are exhausted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PessimisticLockDemoService {

    private final FlashSaleProductRepository flashSaleProductRepository;

    @Transactional
    public Map<String, Object> deductStock(Long productId) {
        String threadName = Thread.currentThread().getName();
        long start = System.currentTimeMillis();

        log.info("[悲觀鎖] {} 嘗試獲取悲觀鎖...", threadName);

        FlashSaleProduct product = flashSaleProductRepository
                .findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        long waitMs = System.currentTimeMillis() - start;

        int before = product.getFlashStock();
        log.info("[悲觀鎖] {} 獲取鎖成功, 等待{}ms, 庫存前={}", threadName, waitMs, before);

        if (product.getFlashStock() <= 0) {
            log.info("[悲觀鎖] {} 庫存不足", threadName);
            return Map.of("thread", threadName, "waitMs", waitMs,
                    "before", before, "after", 0, "success", false,
                    "lock", "PESSIMISTIC_WRITE");
        }

        product.setFlashStock(product.getFlashStock() - 1);
        flashSaleProductRepository.save(product);

        log.info("[悲觀鎖] {} 扣減成功, 庫存後={}", threadName, product.getFlashStock());
        return Map.of("thread", threadName, "waitMs", waitMs,
                "before", before, "after", product.getFlashStock(),
                "success", true, "lock", "PESSIMISTIC_WRITE");
    }
}
