package com.flashsale.common;

import com.flashsale.common.enums.FlashSaleStatus;

import java.time.LocalDateTime;

/**
 * Storefront badge label for a flash SKU. Status + window + stock, not status alone.
 */
public final class FlashSaleDisplay {

    private FlashSaleDisplay() {
    }

    public static String label(Integer status, Integer stock,
                               LocalDateTime startTime, LocalDateTime endTime,
                               LocalDateTime now) {
        if (now == null) {
            now = LocalDateTime.now();
        }
        if (status != null && status == FlashSaleStatus.ENDED.getCode()) {
            return "已結束";
        }
        if (endTime != null && now.isAfter(endTime)) {
            return "已結束";
        }
        if (stock != null && stock <= 0) {
            return "已售罄";
        }
        if (status != null && status == FlashSaleStatus.IN_PROGRESS.getCode()
                && startTime != null && !now.isBefore(startTime)) {
            return "進行中";
        }
        return "即將開始";
    }

    public static String badgeClass(Integer status, Integer stock,
                                    LocalDateTime startTime, LocalDateTime endTime,
                                    LocalDateTime now) {
        return switch (label(status, stock, startTime, endTime, now)) {
            case "進行中" -> "bg-success";
            case "已結束" -> "bg-dark";
            case "已售罄" -> "bg-warning text-dark";
            default -> "bg-secondary";
        };
    }
}
