package com.tindev.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Billing settings are deliberately optional so local development can run
 * without a Stripe account. The billing endpoints validate the values again
 * before making a remote call.
 */
@ConfigurationProperties(prefix = "app.stripe")
public record StripeProperties(
        boolean enabled,
        String secretKey,
        String webhookSecret,
        String basicPriceId,
        String proPriceId,
        String appUrl
) {
}
