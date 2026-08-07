package com.flashsale.common.enums;

import lombok.Getter;

@Getter
public enum FlashSaleStatus {
    NOT_STARTED(0, "未開始"),
    IN_PROGRESS(1, "進行中"),
    ENDED(2, "已結束");

    private final int code;
    private final String desc;

    FlashSaleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
