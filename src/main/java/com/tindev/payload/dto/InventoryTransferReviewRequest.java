package com.tindev.payload.dto;

import jakarta.validation.constraints.Size;

public record InventoryTransferReviewRequest(
        @Size(max = 500) String note
) {
}
