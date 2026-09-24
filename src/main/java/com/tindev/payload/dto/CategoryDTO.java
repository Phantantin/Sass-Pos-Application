package com.tindev.payload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {

    private Long id;

    @NotBlank(message = "Tên danh mục là bắt buộc")
    @Size(max = 120, message = "Tên danh mục không được vượt quá 120 ký tự")
    private String name;

//    private Store store;

    private Long storeId;
}
