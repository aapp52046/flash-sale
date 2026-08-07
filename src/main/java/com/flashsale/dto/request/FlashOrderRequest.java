package com.flashsale.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlashOrderRequest {
    @NotNull(message = "秒殺商品ID不能為空")
    private Long flashProductId;

    @Min(value = 1, message = "數量至少為1")
    @Builder.Default
    private Integer quantity = 1;
}
