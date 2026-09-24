package com.tindev.payload.dto;

import com.tindev.domain.InventoryTransferStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryTransferDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private Long sourceStoreId;
    private String sourceStoreName;
    private Long sourceBranchId;
    private String sourceBranchName;
    private Long destinationStoreId;
    private String destinationStoreName;
    private Long destinationBranchId;
    private String destinationBranchName;
    private Integer quantity;
    private InventoryTransferStatus status;
    private String reason;
    private String reviewNote;
    private Long requestedById;
    private String requestedByName;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;
    private boolean canApprove;
    private boolean canReject;
    private boolean canCancel;
}
