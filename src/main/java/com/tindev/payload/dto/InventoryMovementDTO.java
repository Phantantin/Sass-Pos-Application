package com.tindev.payload.dto;

import com.tindev.domain.InventoryMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementDTO {
    private Long id;
    private Long branchId;
    private Long productId;
    private ProductDTO product;
    private Long performedById;
    private String performedByName;
    private InventoryMovementType type;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private Integer quantityDelta;
    private String reason;
    private LocalDateTime createdAt;
}
