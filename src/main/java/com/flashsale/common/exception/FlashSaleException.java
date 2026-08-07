package com.flashsale.common.exception;

public class FlashSaleException extends RuntimeException {
    private final int code;

    public FlashSaleException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
