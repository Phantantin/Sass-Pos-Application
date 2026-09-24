package com.tindev.service.impl;

import com.tindev.domain.SubscriptionPlan;
import com.tindev.domain.SubscriptionStatus;
import com.tindev.configuration.StripeProperties;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Store;
import com.tindev.modal.StoreSubscription;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.StoreSubscriptionRepository;
import com.tindev.service.UserService;
import com.tindev.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private StoreSubscriptionRepository subscriptionRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserService userService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private StripeProperties stripeProperties;
    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void newStoreReceivesFreeTrialSubscription() {
        Store store = store(10L);
        when(subscriptionRepository.existsByStoreId(store.getId())).thenReturn(false);
        when(subscriptionRepository.save(any(StoreSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.initializeTrial(store);

        ArgumentCaptor<StoreSubscription> captor = ArgumentCaptor.forClass(StoreSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        StoreSubscription saved = captor.getValue();
        assertEquals(SubscriptionPlan.FREE, saved.getPlan());
        assertEquals(SubscriptionStatus.TRIALING, saved.getStatus());
        assertNotNull(saved.getTrialStart());
        assertEquals(saved.getTrialStart().plusDays(14), saved.getTrialEnd());
    }

    @Test
    void freePlanDoesNotLimitBranchesEmployeesOrProducts() {
        Store store = store(10L);
        StoreSubscription subscription = StoreSubscription.builder()
                .store(store)
                .plan(SubscriptionPlan.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(1))
                .build();
        when(subscriptionRepository.findByStoreId(store.getId())).thenReturn(Optional.of(subscription));
        subscriptionService.assertCanCreateBranch(store);
        subscriptionService.assertCanCreateEmployee(store);
        subscriptionService.assertCanCreateProduct(store);

        assertEquals(-1, SubscriptionPlan.FREE.branchLimit());
        assertEquals(-1, SubscriptionPlan.FREE.employeeLimit());
        assertEquals(-1, SubscriptionPlan.FREE.productLimit());
    }

    @Test
    void disabledStripeRejectsWebhookWithoutTryingToProcessItsPayload() {
        when(stripeProperties.enabled()).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> subscriptionService.handleStripeWebhook("{}", "untrusted-signature"));

        assertEquals(503, exception.status().value());
    }

    private Store store(Long id) {
        Store store = new Store();
        store.setId(id);
        return store;
    }
}
