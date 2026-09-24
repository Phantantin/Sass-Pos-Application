package com.tindev.modal;

import com.tindev.domain.PaymentType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentSummary {

    private PaymentType paymentType;
    private BigDecimal totalAmount;
    private int transactionCount;
    private double percentage;
}
