package com.tindev.payload.dto;

import com.tindev.domain.PaymentType;
import com.tindev.domain.OrderStatus;
import com.tindev.modal.Branch;
import com.tindev.modal.Customer;
import com.tindev.modal.OrderItem;
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
public class OrderDTO {

    private Long id;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long branchId;

    private Long customerId;

    private BranchDTO branch;

    private UserDto cashier;

    private Customer customer;

    private PaymentType paymentType;

    private OrderStatus status;

    private String idempotencyKey;

    private List<OrderItemDTO> items;
}
