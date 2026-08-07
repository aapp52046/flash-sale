package com.flashsale.service;

import com.flashsale.entity.FlashOrder;
import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.repository.FlashOrderRepository;
import com.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Phase 8 — Production-style flash sale with five defense layers:
 * <pre>
 *   L0  Frontend throttle
 *   L0.5 Nginx rate limit
 *   L1  synchronized (per-product local gate)
 *   L2  Redisson lock + Lua atomic stock
 *   L3  JPA @Version optimistic lock
 *   L4  SELECT FOR UPDATE pessimistic fallback
 *   +   DB unique (user_id, flash_product_id) final guard
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private static final String STOCK_KEY_PREFIX = "flash:stock:";
    private static final String DEDUP_KEY_PREFIX = "flash:dedup:";
    private static final int MAX_OPTIMISTIC_RETRY = 3;

    private final FlashSaleProductRepository flashSaleProductRepository;
    private final FlashOrderRepository flashOrderRepository;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> deductStockScript;
    private final TransactionTemplate transactionTemplate;

    public Map<String, Object> executeSeckill(Long userId, Long flashProductId) {
        FlashSaleProduct flash = flashSaleProductRepository.findById(flashProductId)
                .orElseThrow(() -> new IllegalArgumentException("秒殺商品不存在"));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(flash.getStartTime())) {
            return result(400, "秒殺尚未開始");
        }
        if (now.isAfter(flash.getEndTime())) {
            return result(400, "秒殺已結束");
        }
        if (flash.getStatus() == null || flash.getStatus() != 1) {
            return result(400, "秒殺未開放");
        }

        // ═══ Layer 1: local lock (fast gate within single JVM) ═══
        String localLockKey = ("FLASH_" + flashProductId).intern();
        synchronized (localLockKey) {

            // ═══ Layer 2: Redisson distributed lock (one-order-per-user) ═══
            String lockKey = "flash:lock:" + userId + ":" + flashProductId;
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                    log.warn("獲取分佈式鎖失敗: userId={}, productId={}", userId, flashProductId);
                    return result(409, "請求處理中，請勿重複提交");
                }

                String dedupKey = DEDUP_KEY_PREFIX + userId + ":" + flashProductId;
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(dedupKey))) {
                    return result(409, "已參與過本場秒殺");
                }

                // ═══ Layer 2b: Redis Lua atomic stock deduct ═══
                String stockKey = STOCK_KEY_PREFIX + flashProductId;
                Long remain = stringRedisTemplate.execute(
                        deductStockScript,
                        Collections.singletonList(stockKey),
                        "1"
                );

                boolean redisDeducted = false;
                if (remain == null || remain == -1L) {
                    log.warn("Redis庫存未預熱: productId={}, 降級走 DB", flashProductId);
                } else if (remain == -2L) {
                    return result(429, "已售罄");
                } else {
                    // remain >= 0 → Lua deducted successfully
                    redisDeducted = true;
                }

                try {
                    Map<String, Object> orderResult = deductDbWithRetry(
                            userId, flashProductId, flash, now, dedupKey);
                    if (orderResult != null && Integer.valueOf(429).equals(orderResult.get("code")) && redisDeducted) {
                        rollbackRedisStock(stockKey);
                    }
                    if (orderResult != null && Integer.valueOf(500).equals(orderResult.get("code")) && redisDeducted) {
                        rollbackRedisStock(stockKey);
                    }
                    return orderResult != null ? orderResult : result(500, "庫存扣減失敗");
                } catch (DataIntegrityViolationException e) {
                    if (redisDeducted) {
                        rollbackRedisStock(stockKey);
                    }
                    return result(409, "已參與過本場秒殺");
                } catch (RuntimeException e) {
                    if (redisDeducted) {
                        rollbackRedisStock(stockKey);
                    }
                    throw e;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result(500, "系統繁忙");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Each optimistic attempt uses a fresh transaction so a version conflict
     * does not mark the whole outer unit as rollback-only.
     */
    private Map<String, Object> deductDbWithRetry(
            Long userId,
            Long flashProductId,
            FlashSaleProduct flashSnapshot,
            LocalDateTime now,
            String dedupKey
    ) {
        for (int retry = 1; retry <= MAX_OPTIMISTIC_RETRY; retry++) {
            final int attempt = retry;
            try {
                return transactionTemplate.execute(status ->
                        deductOptimisticAndCreateOrder(userId, flashProductId, flashSnapshot, now, dedupKey, attempt));
            } catch (OptimisticLockingFailureException e) {
                log.warn("樂觀鎖衝突: attempt={}/{}", attempt, MAX_OPTIMISTIC_RETRY);
                if (attempt == MAX_OPTIMISTIC_RETRY) {
                    return transactionTemplate.execute(status ->
                            deductPessimisticAndCreateOrder(userId, flashProductId, flashSnapshot, now, dedupKey));
                }
            }
        }
        return result(500, "庫存扣減失敗");
    }

    private Map<String, Object> deductOptimisticAndCreateOrder(
            Long userId,
            Long flashProductId,
            FlashSaleProduct flashSnapshot,
            LocalDateTime now,
            String dedupKey,
            int attempt
    ) {
        FlashSaleProduct product = flashSaleProductRepository
                .findById(flashProductId).orElseThrow();
        if (product.getFlashStock() <= 0) {
            return result(429, "已售罄");
        }
        product.setFlashStock(product.getFlashStock() - 1);
        flashSaleProductRepository.saveAndFlush(product);
        log.debug("樂觀鎖扣庫存成功 attempt={}", attempt);
        return createOrder(userId, flashProductId, flashSnapshot, now, dedupKey);
    }

    private Map<String, Object> deductPessimisticAndCreateOrder(
            Long userId,
            Long flashProductId,
            FlashSaleProduct flashSnapshot,
            LocalDateTime now,
            String dedupKey
    ) {
        FlashSaleProduct product = flashSaleProductRepository
                .findByIdWithPessimisticLock(flashProductId)
                .orElseThrow();
        if (product.getFlashStock() <= 0) {
            return result(429, "已售罄");
        }
        product.setFlashStock(product.getFlashStock() - 1);
        flashSaleProductRepository.saveAndFlush(product);
        log.info("悲觀鎖 fallback 成功");
        return createOrder(userId, flashProductId, flashSnapshot, now, dedupKey);
    }

    private Map<String, Object> createOrder(
            Long userId,
            Long flashProductId,
            FlashSaleProduct flashSnapshot,
            LocalDateTime now,
            String dedupKey
    ) {
        String orderNo = generateOrderNo();
        FlashOrder order = FlashOrder.builder()
                .orderNo(orderNo)
                .userId(userId)
                .flashProductId(flashProductId)
                .quantity(1)
                .amount(flashSnapshot.getFlashPrice())
                .status(0)
                .build();
        flashOrderRepository.saveAndFlush(order);

        Duration ttl = Duration.between(now, flashSnapshot.getEndTime());
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(1);
        }
        stringRedisTemplate.opsForValue().set(dedupKey, orderNo, ttl);

        log.info("秒殺成功: userId={}, orderNo={}, productId={}", userId, orderNo, flashProductId);

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("flashProductId", flashProductId);
        data.put("amount", flashSnapshot.getFlashPrice());

        Map<String, Object> body = new HashMap<>();
        body.put("code", 200);
        body.put("message", "搶購成功");
        body.put("data", data);
        return body;
    }

    private void rollbackRedisStock(String stockKey) {
        try {
            stringRedisTemplate.opsForValue().increment(stockKey);
            log.info("Redis庫存回補: {}", stockKey);
        } catch (Exception e) {
            log.error("Redis庫存回補失敗: {}", stockKey, e);
        }
    }

    private String generateOrderNo() {
        return "FS" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private Map<String, Object> result(int code, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }
}
