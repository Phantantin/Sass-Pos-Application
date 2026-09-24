package com.tindev.payload.dto;

import com.tindev.domain.OrderStatus;
import com.tindev.domain.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A paginated, permission-scoped order history for one customer. Refund totals
 * are calculated from persisted refund records rather than client input.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHistoryDTO {
    private Long customerId;
    private String customerName;
    private int page;
    private int pageSize;
    private long totalOrders;
    private int totalPages;
    private List<OrderHistoryItem> orders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderHistoryItem {
        private Long orderId;
        private Long branchId;
        private String branchName;
        private LocalDateTime createdAt;
        private PaymentType paymentType;
        private OrderStatus status;
        private BigDecimal totalAmount;
        private BigDecimal refundedAmount;
        private BigDecimal netAmount;
    }
}
