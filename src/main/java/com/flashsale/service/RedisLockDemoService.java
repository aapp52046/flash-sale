package com.flashsale.service;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Phase 5 — Distributed lock via Redisson.
 * <p>
 * Cross-JVM mutual exclusion. Suitable for multi-instance deployments
 * behind Nginx load balancing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockDemoService {

    private final RedissonClient redissonClient;
    private final FlashSaleProductRepository flashSaleProductRepository;

    @Transactional
    public Map<String, Object> deductStock(Long productId) {
        String threadName = Thread.currentThread().getName();
        RLock lock = redissonClient.getLock("lock:test:" + productId);

        try {
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.info("[Redis鎖] {} 獲取鎖成功", threadName);

                FlashSaleProduct product = flashSaleProductRepository.findById(productId)
                        .orElseThrow(() -> new RuntimeException("商品不存在"));

                int before = product.getFlashStock();
                log.info("[Redis鎖] {} 庫存前={}", threadName, before);

                if (product.getFlashStock() <= 0) {
                    log.info("[Redis鎖] {} 庫存不足", threadName);
                    return Map.of("thread", threadName, "before", before, "after", 0,
                            "success", false, "lock", "Redisson");
                }

                product.setFlashStock(product.getFlashStock() - 1);
                flashSaleProductRepository.save(product);

                log.info("[Redis鎖] {} 扣減成功, 庫存後={}", threadName, product.getFlashStock());
                return Map.of("thread", threadName, "before", before,
                        "after", product.getFlashStock(), "success", true, "lock", "Redisson");
            } else {
                log.warn("[Redis鎖] {} 獲取鎖失敗(競爭中)", threadName);
                return Map.of("thread", threadName, "success", false,
                        "message", "鎖競爭失敗", "lock", "Redisson");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("thread", threadName, "success", false,
                    "message", "被中斷", "lock", "Redisson");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[Redis鎖] {} 釋放鎖", threadName);
            }
        }
    }
}
