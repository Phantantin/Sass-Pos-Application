package com.tindev.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {
    private Long storeId;
    private Long branchId;
    private BigDecimal salesToday;
    private BigDecimal refundsToday;
    private BigDecimal netSalesToday;
    private long ordersToday;
    private long lowStockItems;
    private List<OrderDTO> recentOrders;
}
