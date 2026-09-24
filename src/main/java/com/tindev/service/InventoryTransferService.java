package com.tindev.service;

import com.tindev.payload.dto.InventoryTransferCreateRequest;
import com.tindev.payload.dto.InventoryTransferDTO;
import com.tindev.payload.dto.InventoryTransferReviewRequest;
import com.tindev.payload.dto.TransferAvailabilityDTO;

import java.util.List;

public interface InventoryTransferService {
    List<TransferAvailabilityDTO> findAvailability(Long productId, Long destinationBranchId);
    InventoryTransferDTO create(InventoryTransferCreateRequest request);
    List<InventoryTransferDTO> getVisibleRequests();
    InventoryTransferDTO approve(Long id, InventoryTransferReviewRequest request);
    InventoryTransferDTO reject(Long id, InventoryTransferReviewRequest request);
    InventoryTransferDTO cancel(Long id);
}
