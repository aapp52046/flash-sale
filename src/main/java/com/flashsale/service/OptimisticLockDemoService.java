package com.flashsale.service;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 6 — Optimistic lock via JPA {@code @Version}.
 * <p>
 * Each retry runs in a fresh transaction ({@link TransactionTemplate}) so that
 * {@link OptimisticLockingFailureException} does not poison the outer TX.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimisticLockDemoService {

    private static final int MAX_RETRY = 3;

    private final FlashSaleProductRepository flashSaleProductRepository;
    private final TransactionTemplate transactionTemplate;

    public Map<String, Object> deductStock(Long productId) {
        String threadName = Thread.currentThread().getName();

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            final int currentAttempt = attempt;
            try {
                return transactionTemplate.execute(status -> doDeduct(productId, threadName, currentAttempt));
            } catch (OptimisticLockingFailureException e) {
                log.warn("[樂觀鎖] {} 第{}次衝突, 重試中...", threadName, attempt);
                if (attempt == MAX_RETRY) {
                    log.error("[樂觀鎖] {} {}次全失敗, 應降級悲觀鎖", threadName, MAX_RETRY);
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("thread", threadName);
                    fail.put("attempts", attempt);
                    fail.put("success", false);
                    fail.put("lock", "@Version");
                    fail.put("message", "樂觀鎖重試" + MAX_RETRY + "次全失敗");
                    return fail;
                }
            }
        }

        return Map.of("thread", threadName, "success", false,
                "lock", "@Version", "message", "未知錯誤");
    }

    private Map<String, Object> doDeduct(Long productId, String threadName, int attempt) {
        FlashSaleProduct product = flashSaleProductRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        int before = product.getFlashStock();
        log.info("[樂觀鎖] {} 第{}次嘗試, 庫存前={}, version={}",
                threadName, attempt, before, product.getVersion());

        if (product.getFlashStock() <= 0) {
            return Map.of("thread", threadName, "attempts", attempt,
                    "before", before, "after", 0, "success", false,
                    "lock", "@Version", "message", "庫存不足");
        }

        product.setFlashStock(product.getFlashStock() - 1);
        flashSaleProductRepository.saveAndFlush(product);

        log.info("[樂觀鎖] {} 第{}次成功, 庫存後={}", threadName, attempt, product.getFlashStock());
        return Map.of("thread", threadName, "attempts", attempt,
                "before", before, "after", product.getFlashStock(),
                "success", true, "lock", "@Version");
    }
}
