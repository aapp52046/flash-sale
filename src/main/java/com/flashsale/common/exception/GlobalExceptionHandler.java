
package com.flashsale.common.exception;

import com.flashsale.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Map<Integer, HttpStatus> HTTP_STATUS_BY_CODE = Map.of(
            400, HttpStatus.BAD_REQUEST,
            401, HttpStatus.UNAUTHORIZED,
            404, HttpStatus.NOT_FOUND,
            409, HttpStatus.CONFLICT,
            429, HttpStatus.TOO_MANY_REQUESTS,
            500, HttpStatus.INTERNAL_SERVER_ERROR
    );

    @ExceptionHandler(FlashSaleException.class)
    public ResponseEntity<ApiResponse<Void>> handleFlashSale(FlashSaleException ex) {
        HttpStatus status = HTTP_STATUS_BY_CODE.getOrDefault(ex.getCode(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "伺服器內部錯誤: " + ex.getMessage()));
    }
}
