package com.tindev.payload.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryTransferCreateRequest(
        @NotNull Long productId,
        @NotNull Long sourceBranchId,
        @NotNull Long destinationBranchId,
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @NotBlank String reason
) {
}
