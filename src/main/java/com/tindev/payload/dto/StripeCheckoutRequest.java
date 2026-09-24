package com.tindev.payload.dto;

import com.tindev.domain.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

public record StripeCheckoutRequest(@NotNull SubscriptionPlan plan) {
}
