package com.tindev.mapper;

import com.tindev.modal.StoreSubscription;
import com.tindev.payload.dto.SubscriptionDTO;

public final class SubscriptionMapper {
    private SubscriptionMapper() {
    }

    public static SubscriptionDTO toDTO(StoreSubscription subscription) {
        return SubscriptionDTO.builder()
                .id(subscription.getId())
                .storeId(subscription.getStore().getId())
                .storeBrand(subscription.getStore().getBrand())
                .plan(subscription.getPlan())
                .status(subscription.getStatus())
                .trialStart(subscription.getTrialStart())
                .trialEnd(subscription.getTrialEnd())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .branchLimit(subscription.getPlan().branchLimit())
                .employeeLimit(subscription.getPlan().employeeLimit())
                .productLimit(subscription.getPlan().productLimit())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }
}
