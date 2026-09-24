package com.tindev.payload.dto;

import com.tindev.domain.PayrollStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PayrollDTO {
    private Long id;
    @NotNull private Long employeeId;
    private String employeeName;
    private Long storeId;
    private String storeName;
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;
    @NotNull @DecimalMin("0.00") private BigDecimal baseSalary;
    @DecimalMin("0.00") private BigDecimal hourlyRate;
    private Integer workedMinutes;
    @DecimalMin("0.00") private BigDecimal bonus;
    @DecimalMin("0.00") private BigDecimal deduction;
    private BigDecimal netSalary;
    private PayrollStatus status;
    private LocalDateTime paidAt;
    @Size(max = 500) private String note;
}
