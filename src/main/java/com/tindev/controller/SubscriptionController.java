package com.tindev.controller;

import com.tindev.payload.dto.SubscriptionDTO;
import com.tindev.payload.dto.SubscriptionUpdateRequest;
import com.tindev.payload.dto.StripeCheckoutRequest;
import com.tindev.payload.dto.StripeSessionResponse;
import com.tindev.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @GetMapping("/current")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<SubscriptionDTO> getCurrentSubscription() {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription());
    }

    @GetMapping("/store/{storeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionDTO> getSubscriptionForStore(@PathVariable Long storeId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionForStore(storeId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubscriptionDTO>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions());
    }

    @PutMapping("/store/{storeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionDTO> updateSubscription(@PathVariable Long storeId,
                                                               @Valid @RequestBody SubscriptionUpdateRequest request) {
        return ResponseEntity.ok(subscriptionService.updateSubscription(storeId, request));
    }

    @PostMapping("/stripe/checkout")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<StripeSessionResponse> createStripeCheckout(@Valid @RequestBody StripeCheckoutRequest request) {
        return ResponseEntity.ok(subscriptionService.createStripeCheckout(request.plan()));
    }

    @PostMapping("/stripe/portal")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<StripeSessionResponse> createStripePortalSession() {
        return ResponseEntity.ok(subscriptionService.createStripePortalSession());
    }

    /** Stripe signs the raw request body; this route intentionally has no JWT requirement. */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> handleStripeWebhook(@RequestBody String payload,
                                                      @RequestHeader("Stripe-Signature") String signature) {
        subscriptionService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
