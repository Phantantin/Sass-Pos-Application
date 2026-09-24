package com.tindev.payload.dto;

import com.tindev.domain.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Server-side aggregate for the sales reporting screen. Monetary amounts are
 * always calculated from persisted orders and refunds, never supplied by the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDTO {
    private LocalDate from;
    private LocalDate to;
    private Long storeId;
    private Long branchId;
    private BigDecimal grossSales;
    private BigDecimal refunds;
    private BigDecimal netSales;
    private BigDecimal costOfGoodsSold;
    private BigDecimal refundedCost;
    private BigDecimal netCost;
    private BigDecimal netProfit;
    private BigDecimal averageOrderValue;
    private long orderCount;
    private List<DailySales> dailySales;
    private List<PaymentBreakdown> paymentBreakdown;
    private List<TopProduct> topProducts;
    private List<BranchPerformance> branchPerformance;
    private List<StorePerformance> storePerformance;
    private List<ShiftSummary> shiftReports;
    private List<EmployeePerformance> employeePerformance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailySales {
        private LocalDate date;
        private BigDecimal grossSales;
        private BigDecimal refunds;
        private BigDecimal netSales;
        private BigDecimal netCost;
        private BigDecimal netProfit;
        private long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentBreakdown {
        private PaymentType paymentType;
        private BigDecimal amount;
        private long orderCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopProduct {
        private Long productId;
        private String productName;
        private String sku;
        private long quantity;
        private BigDecimal grossSales;
        private BigDecimal refunds;
        private BigDecimal netSales;
        private BigDecimal netCost;
        private BigDecimal netProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchPerformance {
        private Long storeId;
        private String storeName;
        private Long branchId;
        private String branchName;
        private long orderCount;
        private BigDecimal grossSales;
        private BigDecimal refunds;
        private BigDecimal netSales;
        private BigDecimal netCost;
        private BigDecimal netProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorePerformance {
        private Long storeId;
        private String storeName;
        private long orderCount;
        private BigDecimal grossSales;
        private BigDecimal refunds;
        private BigDecimal netSales;
        private BigDecimal netCost;
        private BigDecimal netProfit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftSummary {
        private Long shiftId;
        private Long branchId;
        private String branchName;
        private Long cashierId;
        private String cashierName;
        private java.time.LocalDateTime shiftStart;
        private java.time.LocalDateTime shiftEnd;
        private BigDecimal totalSales;
        private BigDecimal totalRefund;
        private BigDecimal netSale;
        private int totalOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeePerformance {
        private Long employeeId;
        private String employeeName;
        private long shiftCount;
        private long orderCount;
        private BigDecimal grossSales;
        private BigDecimal refunds;
        private BigDecimal netSales;
        private BigDecimal averageOrderValue;
    }
}
