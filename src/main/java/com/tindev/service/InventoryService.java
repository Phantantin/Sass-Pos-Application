package com.tindev.service;

import com.tindev.domain.InventoryMovementType;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryDTO;
import com.tindev.payload.dto.InventoryMovementDTO;

import java.util.List;

public interface InventoryService {

    InventoryDTO createInventory(InventoryDTO inventoryDTO);
    InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO);
    void deleteInventory(Long id, String reason);
    InventoryDTO getInventoryById(Long id);
    InventoryDTO getInventoryByProductIdAndBranchId(Long productId, Long branchId);
    List<InventoryDTO> getAllInventoryByBranchId(Long branchId);
    List<InventoryMovementDTO> getMovementHistory(Long branchId, Long productId);

    /**
     * Điều chỉnh tồn kho trong transaction hiện tại và ghi một dòng audit.
     * Các nghiệp vụ order/refund phải gọi method này thay vì ghi trực tiếp
     * InventoryRepository để không làm thất lạc lịch sử kho.
     */
    InventoryDTO adjustInventoryForTransaction(Long productId, Long branchId, int delta,
                                               InventoryMovementType type, String reason, User performedBy);
}
