package com.tindev.payload.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDTO {

    private Long id;

    private BranchDTO branch;

    private Long branchId;

    private Long productId;

    private ProductDTO product;

    @NotNull(message = "Số lượng tồn kho là bắt buộc")
    @Min(value = 0, message = "Số lượng tồn kho phải lớn hơn hoặc bằng 0")
    private Integer quantity;

    @Min(value = 0, message = "Ngưỡng tồn tối thiểu phải lớn hơn hoặc bằng 0")
    private Integer minStockLevel;

    /** Lý do bắt buộc khi điều chỉnh thủ công số lượng tồn kho. */
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;

    private LocalDateTime lastUpdate;
}
