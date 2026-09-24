package com.tindev.repository;

import com.tindev.modal.StoreSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreSubscriptionRepository extends JpaRepository<StoreSubscription, Long> {
    Optional<StoreSubscription> findByStoreId(Long storeId);
    Optional<StoreSubscription> findByStripeCustomerId(String stripeCustomerId);
    Optional<StoreSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    boolean existsByStoreId(Long storeId);
}
