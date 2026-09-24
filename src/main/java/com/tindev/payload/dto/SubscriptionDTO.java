package com.tindev.payload.dto;

import com.tindev.domain.SubscriptionPlan;
import com.tindev.domain.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private Long id;
    private Long storeId;
    private String storeBrand;
    private SubscriptionPlan plan;
    private SubscriptionStatus status;
    private LocalDateTime trialStart;
    private LocalDateTime trialEnd;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private int branchLimit;
    private int employeeLimit;
    private int productLimit;
    private LocalDateTime updatedAt;
}
