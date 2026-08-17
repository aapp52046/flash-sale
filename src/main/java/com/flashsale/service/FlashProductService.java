package com.flashsale.service;

import com.flashsale.common.FlashSaleDisplay;
import com.flashsale.common.enums.FlashSaleStatus;
import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.dto.request.FlashProductRequest;
import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.entity.Product;
import com.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashProductService {

    private static final String STOCK_KEY_PREFIX = "flash:stock:";

    private final FlashSaleProductRepository flashSaleProductRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public FlashSaleProduct create(FlashProductRequest request) {
        if (!productRepository.existsById(request.getProductId())) {
            throw new FlashSaleException(404, "商品不存在");
        }
        if (flashSaleProductRepository.findByProductId(request.getProductId()).isPresent()) {
            throw new FlashSaleException(400, "該商品已設為秒殺商品");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new FlashSaleException(400, "結束時間必須晚於開始時間");
        }

        FlashSaleProduct flash = FlashSaleProduct.builder()
                .productId(request.getProductId())
                .flashPrice(request.getFlashPrice())
                .flashStock(request.getFlashStock())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(FlashSaleStatus.NOT_STARTED.getCode())
                .build();

        return flashSaleProductRepository.save(flash);
    }

    public List<FlashSaleProduct> getActiveFlashProducts() {
        LocalDateTime now = LocalDateTime.now();
        List<FlashSaleProduct> active = flashSaleProductRepository
                .findByStartTimeBeforeAndEndTimeAfterAndStatus(now, now, FlashSaleStatus.IN_PROGRESS.getCode());
        List<FlashSaleProduct> upcoming = flashSaleProductRepository
                .findByStatus(FlashSaleStatus.NOT_STARTED.getCode());
        active.addAll(upcoming);
        return enrichNames(active);
    }

    public FlashSaleProduct getById(Long id) {
        FlashSaleProduct flash = flashSaleProductRepository.findById(id)
                .orElseThrow(() -> new FlashSaleException(404, "秒殺商品不存在"));
        productRepository.findById(flash.getProductId())
                .ifPresent(p -> flash.setProductName(p.getName()));
        enrichDisplay(flash, LocalDateTime.now());
        return flash;
    }

    public List<FlashSaleProduct> getAll() {
        return enrichNames(flashSaleProductRepository.findAll());
    }

    private List<FlashSaleProduct> enrichNames(List<FlashSaleProduct> list) {
        if (list.isEmpty()) return list;
        Map<Long, String> names = productRepository.findAllById(
                list.stream().map(FlashSaleProduct::getProductId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now();
        list.forEach(f -> {
            f.setProductName(names.getOrDefault(f.getProductId(), "商品 #" + f.getProductId()));
            enrichDisplay(f, now);
        });
        return list;
    }

    private void enrichDisplay(FlashSaleProduct flash, LocalDateTime now) {
        flash.setDisplayLabel(FlashSaleDisplay.label(
                flash.getStatus(), flash.getFlashStock(),
                flash.getStartTime(), flash.getEndTime(), now));
        flash.setDisplayBadgeClass(FlashSaleDisplay.badgeClass(
                flash.getStatus(), flash.getFlashStock(),
                flash.getStartTime(), flash.getEndTime(), now));
    }

    public void preheatStock(Long flashProductId) {
        FlashSaleProduct flash = getById(flashProductId);
        String key = STOCK_KEY_PREFIX + flashProductId;
        if (flash.getStatus() != null
                && flash.getStatus() == FlashSaleStatus.IN_PROGRESS.getCode()
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new FlashSaleException(400, "活動進行中，無法覆寫已預熱庫存");
        }
        // Plain string so Lua DECRBY works (not Jackson JSON)
        stringRedisTemplate.opsForValue().set(key, String.valueOf(flash.getFlashStock()));

        Duration ttl = Duration.between(LocalDateTime.now(), flash.getEndTime());
        if (!ttl.isNegative() && !ttl.isZero()) {
            stringRedisTemplate.expire(key, ttl);
        }

        log.info("預熱庫存成功: productId={}, stock={}", flashProductId, flash.getFlashStock());
    }

    @Transactional
    public void updateStatus(Long flashProductId, int status) {
        FlashSaleProduct flash = getById(flashProductId);
        flash.setStatus(status);
        flashSaleProductRepository.save(flash);
        log.info("更新秒殺狀態: productId={}, status={}", flashProductId, status);
    }
}
