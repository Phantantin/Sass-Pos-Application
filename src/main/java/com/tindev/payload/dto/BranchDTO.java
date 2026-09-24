package com.tindev.payload.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDTO {

    private Long id;

    @NotBlank(message = "Tên chi nhánh là bắt buộc")
    @Size(max = 120, message = "Tên chi nhánh không được quá 120 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ chi nhánh là bắt buộc")
    @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
    private String address;

    @NotBlank(message = "Số điện thoại chi nhánh là bắt buộc")
    @Size(max = 30, message = "Số điện thoại không được quá 30 ký tự")
    private String phone;

    @Email(message = "Email chi nhánh không hợp lệ")
    @Size(max = 254, message = "Email không được quá 254 ký tự")
    private String email;

    @NotEmpty(message = "Cần chọn ít nhất một ngày làm việc")
    private List<@NotBlank(message = "Ngày làm việc không được để trống") String> workingDays;

    @NotNull(message = "Giờ mở cửa là bắt buộc")
    private LocalTime openTime;

    @NotNull(message = "Giờ đóng cửa là bắt buộc")
    private LocalTime closeTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private StoreDto store;

    private Long storeId;

    private UserDto manager;
}
