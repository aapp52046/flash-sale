package com.flashsale.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlashSaleDisplayTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 17, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 17, 12, 0);

    @Test
    void publishedAndInWindowIsLive() {
        assertThat(FlashSaleDisplay.label(1, 10, START, END, START.plusHours(1)))
                .isEqualTo("進行中");
        assertThat(FlashSaleDisplay.badgeClass(1, 10, START, END, START.plusHours(1)))
                .isEqualTo("bg-success");
    }

    @Test
    void unpublishedInWindowIsUpcoming() {
        assertThat(FlashSaleDisplay.label(0, 10, START, END, START.plusHours(1)))
                .isEqualTo("即將開始");
    }

    @Test
    void endedStatusWinsOverStock() {
        assertThat(FlashSaleDisplay.label(2, 10, START, END, START.plusHours(1)))
                .isEqualTo("已結束");
        assertThat(FlashSaleDisplay.badgeClass(2, 10, START, END, START.plusHours(1)))
                .isEqualTo("bg-dark");
    }

    @Test
    void pastEndTimeIsEnded() {
        assertThat(FlashSaleDisplay.label(1, 10, START, END, END.plusMinutes(1)))
                .isEqualTo("已結束");
    }

    @Test
    void zeroStockIsSoldOut() {
        assertThat(FlashSaleDisplay.label(1, 0, START, END, START.plusHours(1)))
                .isEqualTo("已售罄");
        assertThat(FlashSaleDisplay.badgeClass(1, 0, START, END, START.plusHours(1)))
                .isEqualTo("bg-warning text-dark");
    }
}
