package com.tindev.mapper;

import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.Product;
import com.tindev.payload.dto.InventoryDTO;

public class InventoryMapper {

    public static InventoryDTO toDTO(Inventory inventory) {
        return InventoryDTO.builder()
                .id(inventory.getId())
                .branchId(inventory.getBranch().getId())
                .productId(inventory.getProduct().getId())
                .product(ProductMapper.toDTO(inventory.getProduct()))
                .quantity(inventory.getQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .lastUpdate(inventory.getLastUpdate())
                .build();
    }
    public static Inventory toEntity(InventoryDTO inventoryDTO,
                                     Branch branch,
                                     Product product) {
        return Inventory.builder()
                .branch(branch)
                .product(product)
                .quantity(inventoryDTO.getQuantity())
                .minStockLevel(inventoryDTO.getMinStockLevel() == null ? 5 : inventoryDTO.getMinStockLevel())
                .build();
    }
}
