package com.flashsale.service;

import com.flashsale.common.exception.FlashSaleException;
import com.flashsale.entity.FlashOrder;
import com.flashsale.repository.FlashOrderRepository;
import com.flashsale.repository.FlashSaleProductRepository;
import com.flashsale.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashOrderServiceTest {

    @Mock
    private FlashOrderRepository flashOrderRepository;
    @Mock
    private FlashSaleProductRepository flashSaleProductRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private FlashOrderService flashOrderService;

    @Test
    void ownerCanReadOwnOrder() {
        when(flashOrderRepository.findByOrderNo("FS1")).thenReturn(Optional.of(order(7L)));
        when(flashSaleProductRepository.findAllById(any())).thenReturn(List.of());

        FlashOrder got = flashOrderService.getAccessibleOrder("FS1", 7L, false);

        assertThat(got.getOrderNo()).isEqualTo("FS1");
        assertThat(got.getProductName()).isEqualTo("商品 #9");
    }

    @Test
    void strangerGetsNotFound() {
        when(flashOrderRepository.findByOrderNo("FS1")).thenReturn(Optional.of(order(7L)));
        when(flashSaleProductRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> flashOrderService.getAccessibleOrder("FS1", 8L, false))
                .isInstanceOf(FlashSaleException.class)
                .hasMessage("訂單不存在");
    }

    @Test
    void adminCanReadOthersOrder() {
        when(flashOrderRepository.findByOrderNo("FS1")).thenReturn(Optional.of(order(7L)));
        when(flashSaleProductRepository.findAllById(any())).thenReturn(List.of());

        assertThat(flashOrderService.getAccessibleOrder("FS1", 99L, true).getUserId()).isEqualTo(7L);
    }

    private static FlashOrder order(Long userId) {
        return FlashOrder.builder()
                .orderNo("FS1")
                .userId(userId)
                .flashProductId(9L)
                .build();
    }
}
