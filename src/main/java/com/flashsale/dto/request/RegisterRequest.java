package com.flashsale.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "帳號不能為空")
    @Size(min = 3, max = 64, message = "帳號長度 3-64 字元")
    private String username;

    @NotBlank(message = "密碼不能為空")
    @Size(min = 6, max = 64, message = "密碼長度 6-64 字元")
    private String password;
}
