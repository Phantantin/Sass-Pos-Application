package com.tindev.payload.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferAvailabilityDTO {
    private Long productId;
    private String productName;
    private String sku;
    private String image;
    private Long sourceStoreId;
    private String sourceStoreName;
    private Long sourceBranchId;
    private String sourceBranchName;
    private Integer availableQuantity;
}
