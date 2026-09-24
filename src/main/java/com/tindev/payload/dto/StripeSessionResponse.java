package com.tindev.payload.dto;

/** A short-lived URL returned by Stripe Checkout or the Customer Portal. */
public record StripeSessionResponse(String url) {
}
