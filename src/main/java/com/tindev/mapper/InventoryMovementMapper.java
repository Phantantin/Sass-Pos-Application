package com.tindev.mapper;

import com.tindev.modal.InventoryMovement;
import com.tindev.payload.dto.InventoryMovementDTO;

public final class InventoryMovementMapper {
    private InventoryMovementMapper() {
    }

    public static InventoryMovementDTO toDTO(InventoryMovement movement) {
        return InventoryMovementDTO.builder()
                .id(movement.getId())
                .branchId(movement.getBranch().getId())
                .productId(movement.getProduct().getId())
                .product(ProductMapper.toDTO(movement.getProduct()))
                .performedById(movement.getPerformedBy().getId())
                .performedByName(movement.getPerformedBy().getFullName())
                .type(movement.getType())
                .quantityBefore(movement.getQuantityBefore())
                .quantityAfter(movement.getQuantityAfter())
                .quantityDelta(movement.getQuantityDelta())
                .reason(movement.getReason())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
