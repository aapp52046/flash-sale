package com.flashsale.common.exception;

public class StockSoldOutException extends FlashSaleException {
    public StockSoldOutException(String message) {
        super(429, message);
    }
}
