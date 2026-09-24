package com.tindev.payload.dto;

import com.tindev.domain.StoreStatus;
import com.tindev.modal.StoreContact;
import com.tindev.modal.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoreDto {

    private Long id;

    @NotBlank(message = "Tên cửa hàng là bắt buộc")
    @Size(max = 120, message = "Tên cửa hàng không được quá 120 ký tự")
    private String brand;

    private UserDto storeAdmin;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Size(max = 1000, message = "Mô tả không được quá 1000 ký tự")
    private String description;

    @Size(max = 80, message = "Loại hình cửa hàng không được quá 80 ký tự")
    private String storeType;

    private StoreStatus status;


    private StoreContact contact;

}
