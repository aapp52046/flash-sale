package com.flashsale;

import com.flashsale.entity.FlashSaleProduct;
import com.flashsale.entity.Product;
import com.flashsale.entity.User;
import com.flashsale.repository.FlashOrderRepository;
import com.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.repository.UserRepository;
import com.flashsale.service.FlashProductService;
import com.flashsale.service.FlashSaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in SPEC G1/G2 against real PostgreSQL + Redis.
 * Needs {@code flash_sale_test} on :5432 and Redis on :6379; skipped otherwise.
 * CI starts both as GitHub Actions services. Locally: start Redis via
 * {@code Redis\redis-server.exe}, then {@code DB_PASSWORD=... mvn test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIf("infraAvailable")
@Timeout(90)
class FlashSaleConcurrencyTest {

    private static final int STOCK = 10;
    private static final int USERS = 30;

    @Autowired
    private FlashSaleService flashSaleService;
    @Autowired
    private FlashProductService flashProductService;
    @Autowired
    private FlashOrderRepository flashOrderRepository;
    @Autowired
    private FlashSaleProductRepository flashSaleProductRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private DefaultRedisScript<Long> deductStockScript;

    static boolean infraAvailable() {
        return portOpen("127.0.0.1", 5432) && portOpen("127.0.0.1", 6379);
    }

    @BeforeEach
    void clean() {
        flashOrderRepository.deleteAll();
        flashSaleProductRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = stringRedisTemplate.keys("flash:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void luaContract() {
        assertThat(deduct("flash:stock:missing")).isEqualTo(-1L);

        stringRedisTemplate.opsForValue().set("flash:stock:empty", "0");
        assertThat(deduct("flash:stock:empty")).isEqualTo(-2L);
        assertThat(stringRedisTemplate.opsForValue().get("flash:stock:empty")).isEqualTo("0");

        stringRedisTemplate.opsForValue().set("flash:stock:ok", "5");
        assertThat(deduct("flash:stock:ok")).isEqualTo(4L);
    }

    @Test
    void noOversellWhenDemandExceedsStock() throws Exception {
        Long flashId = seedFlash(STOCK, true);
        List<Long> userIds = seedUsers(USERS);

        Counts counts = runSeckill(userIds, flashId);

        assertThat(counts.success).isEqualTo(STOCK);
        assertThat(counts.soldOut).isEqualTo(USERS - STOCK);
        assertThat(counts.errors).isZero();
        assertStockAndOrders(flashId, STOCK, 0);
    }

    @Test
    void noOversellWhenRedisNotPreheated() throws Exception {
        Long flashId = seedFlash(STOCK, false);
        List<Long> userIds = seedUsers(USERS);

        Counts counts = runSeckill(userIds, flashId);

        assertThat(counts.success).isEqualTo(STOCK);
        assertThat(counts.errors).isZero();
        assertThat(flashOrderRepository.countByFlashProductId(flashId)).isEqualTo(STOCK);
        assertThat(flashSaleProductRepository.findById(flashId).orElseThrow().getFlashStock()).isZero();
    }

    @Test
    void sameUserGetsOnlyOneOrder() throws Exception {
        Long flashId = seedFlash(STOCK, true);
        Long userId = seedUsers(1).get(0);
        List<Long> clicks = Collections.nCopies(8, userId);

        Counts counts = runSeckill(clicks, flashId);

        assertThat(counts.success).isEqualTo(1);
        assertThat(counts.duplicate).isEqualTo(7);
        assertThat(counts.errors).isZero();
        assertThat(flashOrderRepository.countByFlashProductId(flashId)).isEqualTo(1);
        assertThat(flashOrderRepository.existsByUserIdAndFlashProductId(userId, flashId)).isTrue();
    }

    private Long deduct(String key) {
        return stringRedisTemplate.execute(deductStockScript, List.of(key), "1");
    }

    private Long seedFlash(int stock, boolean preheat) {
        Product product = productRepository.save(Product.builder()
                .name("Concurrency SKU")
                .normalPrice(new BigDecimal("100.00"))
                .totalStock(stock)
                .build());
        FlashSaleProduct flash = flashSaleProductRepository.save(FlashSaleProduct.builder()
                .productId(product.getId())
                .flashPrice(new BigDecimal("9.99"))
                .flashStock(stock)
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .status(1)
                .version(0)
                .build());
        if (preheat) {
            flashProductService.preheatStock(flash.getId());
        }
        return flash.getId();
    }

    private List<Long> seedUsers(int n) {
        List<Long> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            User user = userRepository.save(User.builder()
                    .username("u" + i)
                    .password("x")
                    .role("USER")
                    .build());
            ids.add(user.getId());
        }
        return ids;
    }

    private Counts runSeckill(List<Long> userIds, Long flashId) throws InterruptedException {
        int n = userIds.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        try {
            for (Long userId : userIds) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        Map<String, Object> result = flashSaleService.executeSeckill(userId, flashId);
                        int code = (int) result.get("code");
                        switch (code) {
                            case 200 -> success.incrementAndGet();
                            case 429 -> soldOut.incrementAndGet();
                            case 409 -> duplicate.incrementAndGet();
                            default -> errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        return new Counts(success.get(), soldOut.get(), duplicate.get(), errors.get());
    }

    private void assertStockAndOrders(Long flashId, int expectedOrders, int expectedRemain) {
        assertThat(flashOrderRepository.countByFlashProductId(flashId)).isEqualTo(expectedOrders);
        assertThat(flashSaleProductRepository.findById(flashId).orElseThrow().getFlashStock())
                .isEqualTo(expectedRemain);
        assertThat(stringRedisTemplate.opsForValue().get("flash:stock:" + flashId))
                .isEqualTo(String.valueOf(expectedRemain));
    }

    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private record Counts(int success, int soldOut, int duplicate, int errors) {
    }
}
