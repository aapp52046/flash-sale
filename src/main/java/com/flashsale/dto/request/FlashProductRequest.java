package com.flashsale.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FlashProductRequest {
    @NotNull(message = "商品ID不能為空")
    private Long productId;

    @NotNull(message = "秒殺價格不能為空")
    @Min(value = 0, message = "價格不能為負數")
    private BigDecimal flashPrice;

    @NotNull(message = "秒殺庫存不能為空")
    @Min(value = 1, message = "庫存至少為1")
    private Integer flashStock;

    @NotNull(message = "開始時間不能為空")
    private LocalDateTime startTime;

    @NotNull(message = "結束時間不能為空")
    private LocalDateTime endTime;
}
