package com.tindev.payload.dto;

import com.tindev.domain.InventoryMovementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Tenant-scoped aggregate of the immutable inventory movement audit trail.
 * Outbound quantities are returned as positive numbers; {@code netQuantity}
 * retains the signed inventory change.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementReportDTO {
    private LocalDate from;
    private LocalDate to;
    private Long storeId;
    private Long branchId;
    private long movementCount;
    private long inboundQuantity;
    private long outboundQuantity;
    private long netQuantity;
    private List<DailyMovement> dailyMovements;
    private List<MovementTypeBreakdown> typeBreakdown;
    private List<ProductMovement> topProducts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyMovement {
        private LocalDate date;
        private long movementCount;
        private long inboundQuantity;
        private long outboundQuantity;
        private long netQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovementTypeBreakdown {
        private InventoryMovementType type;
        private long movementCount;
        private long inboundQuantity;
        private long outboundQuantity;
        private long netQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductMovement {
        private Long productId;
        private String productName;
        private String sku;
        private long movementCount;
        private long inboundQuantity;
        private long outboundQuantity;
        private long netQuantity;
        private long movementVolume;
    }
}
