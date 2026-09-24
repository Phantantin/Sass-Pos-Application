package com.tindev.modal;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShiftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalSales;
    @Column(precision = 19, scale = 2)
    private BigDecimal totalRefund;
    @Column(precision = 19, scale = 2)
    private BigDecimal netSale;
    private int totalOrder;

    @ManyToOne
    private User cashier;

    @ManyToOne
    private Branch branch;


    @Transient
    private List<PaymentSummary> paymentSummaries;

    @Transient
    private List<Product> topSellingProducts;

    @Transient
    private List<Order> recentOrders;

    @OneToMany(mappedBy = "shiftReport", cascade = CascadeType.ALL)
    private List<Refund> refunds;


}
