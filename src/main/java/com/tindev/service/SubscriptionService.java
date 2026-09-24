package com.tindev.service;

import com.tindev.modal.Store;
import com.tindev.payload.dto.SubscriptionDTO;
import com.tindev.payload.dto.SubscriptionUpdateRequest;
import com.tindev.payload.dto.StripeSessionResponse;
import com.tindev.domain.SubscriptionPlan;

import java.util.List;

public interface SubscriptionService {
    SubscriptionDTO getCurrentSubscription();
    SubscriptionDTO getSubscriptionForStore(Long storeId);
    List<SubscriptionDTO> getAllSubscriptions();
    SubscriptionDTO updateSubscription(Long storeId, SubscriptionUpdateRequest request);
    StripeSessionResponse createStripeCheckout(SubscriptionPlan plan);
    StripeSessionResponse createStripePortalSession();
    void handleStripeWebhook(String payload, String signature);
    void initializeTrial(Store store);
    void deleteForStore(Store store);
    void assertCanCreateBranch(Store store);
    void assertCanCreateEmployee(Store store);
    void assertCanCreateProduct(Store store);
}
