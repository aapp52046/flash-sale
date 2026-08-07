package com.flashsale.common.exception;

public class DuplicateOrderException extends FlashSaleException {
    public DuplicateOrderException(String message) {
        super(409, message);
    }
}
