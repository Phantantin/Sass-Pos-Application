package com.tindev.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundItemDTO {
    private Long id;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private ProductDTO product;
}
