package com.tindev.payload.dto;

import com.tindev.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {


    private Long id;

    @NotBlank(message = "Họ tên là bắt buộc")
    @Size(max = 120, message = "Họ tên không được quá 120 ký tự")
    private String fullName;

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    @Size(max = 254, message = "Email không được quá 254 ký tự")
    private String email;

    @Size(max = 30, message = "Số điện thoại không được quá 30 ký tự")
    private String phone;

    @NotNull(message = "Vai trò là bắt buộc")
    private UserRole role;

    @Size(min = 8, max = 100, message = "Mật khẩu phải có từ 8 đến 100 ký tự")
    private String password;

    private Long branchId;

    private Long storeId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
}
