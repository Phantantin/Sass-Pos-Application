package com.tindev.payload.dto;

import com.tindev.domain.SubscriptionPlan;
import com.tindev.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubscriptionUpdateRequest {
    @NotNull(message = "Gói subscription là bắt buộc")
    private SubscriptionPlan plan;

    @NotNull(message = "Trạng thái subscription là bắt buộc")
    private SubscriptionStatus status;

    private LocalDateTime trialEnd;
    private LocalDateTime currentPeriodEnd;
}
