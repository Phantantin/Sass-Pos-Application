package com.tindev.modal;

import com.tindev.domain.PayrollStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payroll", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "period_start", "period_end"}))
public class Payroll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User employee;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;
    @Column(nullable = false)
    private LocalDate periodStart;
    @Column(nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal baseSalary;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal hourlyRate;
    @Column(nullable = false)
    private Integer workedMinutes;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bonus;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deduction;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netSalary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PayrollStatus status;
    private LocalDateTime paidAt;
    @Column(length = 500)
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist void create() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = PayrollStatus.DRAFT;
    }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}
