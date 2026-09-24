package com.tindev.payload.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tindev.domain.PaymentType;
import com.tindev.modal.Branch;
import com.tindev.modal.Order;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.User;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundDTO {

    private Long id;

    private OrderDTO order;
    private Long orderId;

    private String reason;

    private BigDecimal amount;

//    private ShiftReport shiftReport;
    private Long shiftReportId;

    private UserDto cashier;
    private String cashierName;

    private BranchDTO branch;
    private Long branchId;

    private PaymentType paymentType;

    private LocalDateTime createdAt;

    private List<RefundItemDTO> items;
}
