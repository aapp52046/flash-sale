package com.flashsale.service;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Phase 4 — JVM local lock ({@code synchronized} + {@link String#intern()}).
 * <p>
 * Effective only within a single JVM. Multi-instance deployments will oversell
 * without a distributed lock (see {@link RedisLockDemoService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalLockDemoService {

    private final FlashSaleProductRepository flashSaleProductRepository;

    @Transactional
    public Map<String, Object> deductStock(Long productId) {
        String lockObject = ("PRODUCT_" + productId).intern();

        synchronized (lockObject) {
            String threadName = Thread.currentThread().getName();
            FlashSaleProduct product = flashSaleProductRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            int before = product.getFlashStock();
            log.info("[本地鎖] {} 進入同步塊, 庫存前={}", threadName, before);

            if (product.getFlashStock() <= 0) {
                log.info("[本地鎖] {} 庫存不足", threadName);
                return Map.of("thread", threadName, "before", before, "after", 0,
                        "success", false, "lock", "synchronized");
            }

            product.setFlashStock(product.getFlashStock() - 1);
            flashSaleProductRepository.save(product);

            log.info("[本地鎖] {} 扣減成功, 庫存後={}", threadName, product.getFlashStock());
            return Map.of("thread", threadName, "before", before,
                    "after", product.getFlashStock(), "success", true, "lock", "synchronized");
        }
    }
}
