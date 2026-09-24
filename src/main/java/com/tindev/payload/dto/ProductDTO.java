package com.tindev.payload.dto;

import com.tindev.domain.CatalogStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {

    private Long id;

    @NotBlank(message = "Tên sản phẩm là bắt buộc")
    @Size(max = 255, message = "Tên sản phẩm không được vượt quá 255 ký tự")
    private String name;

    @NotBlank(message = "SKU là bắt buộc")
    @Size(max = 255, message = "SKU không được vượt quá 255 ký tự")
    private String sku;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @DecimalMin(value = "0.00", inclusive = true, message = "MRP không được âm")
    private BigDecimal mrp;

    @DecimalMin(value = "0.00", inclusive = true, message = "Giá vốn không được âm")
    private BigDecimal costPrice;

    @NotNull(message = "Giá bán là bắt buộc")
    @DecimalMin(value = "0.00", inclusive = true, message = "Giá bán không được âm")
    private BigDecimal sellingPrice;

    @Size(max = 255, message = "Thương hiệu không được vượt quá 255 ký tự")
    private String brand;

    @Size(max = 2048, message = "URL ảnh không được vượt quá 2048 ký tự")
    private String image;

    private CategoryDTO category;

    private Long categoryId;

    private Long storeId;

    private CatalogStatus catalogStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
