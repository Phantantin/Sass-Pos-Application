package com.tindev.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.billingportal.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.tindev.configuration.StripeProperties;
import com.tindev.domain.SubscriptionPlan;
import com.tindev.domain.SubscriptionStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.SubscriptionMapper;
import com.tindev.modal.Store;
import com.tindev.modal.StoreSubscription;
import com.tindev.modal.User;
import com.tindev.payload.dto.SubscriptionDTO;
import com.tindev.payload.dto.SubscriptionUpdateRequest;
import com.tindev.payload.dto.StripeSessionResponse;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.StoreSubscriptionRepository;
import com.tindev.service.SubscriptionService;
import com.tindev.service.AuditLogService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {
    private static final int TRIAL_DAYS = 14;

    private final StoreSubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final StripeProperties stripeProperties;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDTO getCurrentSubscription() {
        Store store = storeFor(currentUser());
        return SubscriptionMapper.toDTO(requireSubscription(store));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDTO getSubscriptionForStore(Long storeId) {
        Store store = requireStore(storeId);
        requireReadAccess(currentUser(), store);
        return SubscriptionMapper.toDTO(requireSubscription(store));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDTO> getAllSubscriptions() {
        requireAdmin(currentUser());
        return subscriptionRepository.findAll().stream().map(SubscriptionMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public SubscriptionDTO updateSubscription(Long storeId, SubscriptionUpdateRequest request) {
        User actor = currentUser();
        requireAdmin(actor);
        StoreSubscription subscription = requireSubscription(requireStore(storeId));
        subscription.setPlan(request.getPlan());
        subscription.setStatus(request.getStatus());
        if (request.getTrialEnd() != null) {
            subscription.setTrialEnd(request.getTrialEnd());
        }
        if (request.getCurrentPeriodEnd() != null) {
            subscription.setCurrentPeriodEnd(request.getCurrentPeriodEnd());
        }
        StoreSubscription saved = subscriptionRepository.save(subscription);
        auditLogService.record(saved.getStore(), actor, "SUBSCRIPTION_UPDATED", "SUBSCRIPTION", saved.getId(),
                "Plan=" + saved.getPlan() + ", status=" + saved.getStatus());
        return SubscriptionMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public StripeSessionResponse createStripeCheckout(SubscriptionPlan plan) {
        if (plan == SubscriptionPlan.FREE) {
            throw ApiException.badRequest("Gói FREE không cần thanh toán qua Stripe");
        }
        User owner = currentUser();
        requireStoreAdmin(owner);
        Store store = storeFor(owner);
        StoreSubscription subscription = requireSubscription(store);
        if (!isBlank(subscription.getStripeSubscriptionId())
                && (subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING
                || subscription.getStatus() == SubscriptionStatus.PAST_DUE)) {
            throw ApiException.conflict("Cửa hàng đã có subscription Stripe. Hãy dùng Customer Portal để đổi hoặc hủy gói.");
        }
        configureStripe();
        String priceId = priceIdFor(plan);
        String customerId = ensureStripeCustomer(subscription, store, owner);

        try {
            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(
                    com.stripe.param.checkout.SessionCreateParams.builder()
                            .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                            .setCustomer(customerId)
                            .setClientReferenceId(store.getId().toString())
                            .setSuccessUrl(frontendUrl() + "/vi/subscriptions?checkout=success&session_id={CHECKOUT_SESSION_ID}")
                            .setCancelUrl(frontendUrl() + "/vi/subscriptions?checkout=cancelled")
                            .putMetadata("store_id", store.getId().toString())
                            .putMetadata("plan", plan.name())
                            .setSubscriptionData(com.stripe.param.checkout.SessionCreateParams.SubscriptionData.builder()
                                    .putMetadata("store_id", store.getId().toString())
                                    .putMetadata("plan", plan.name())
                                    .build())
                            .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build())
                            .build());
            if (isBlank(session.getUrl())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe không trả về URL Checkout");
            }
            return new StripeSessionResponse(session.getUrl());
        } catch (StripeException exception) {
            throw stripeFailure(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StripeSessionResponse createStripePortalSession() {
        User owner = currentUser();
        requireStoreAdmin(owner);
        StoreSubscription subscription = requireSubscription(storeFor(owner));
        configureStripe();
        if (isBlank(subscription.getStripeCustomerId())) {
            throw ApiException.conflict("Chưa có khách hàng Stripe. Hãy hoàn tất Checkout của gói trả phí trước.");
        }
        try {
            Session session = Session.create(SessionCreateParams.builder()
                    .setCustomer(subscription.getStripeCustomerId())
                    .setReturnUrl(frontendUrl() + "/vi/subscriptions")
                    .build());
            if (isBlank(session.getUrl())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe không trả về URL Customer Portal");
            }
            return new StripeSessionResponse(session.getUrl());
        } catch (StripeException exception) {
            throw stripeFailure(exception);
        }
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signature) {
        if (!stripeProperties.enabled() || isBlank(stripeProperties.webhookSecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook chưa được cấu hình");
        }
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException exception) {
            throw ApiException.badRequest("Chữ ký Stripe webhook không hợp lệ");
        }

        JsonObject object = webhookObject(payload);
        if (object == null) {
            return;
        }
        switch (event.getType()) {
            case "checkout.session.completed" -> synchronizeCheckout(object);
            case "customer.subscription.created", "customer.subscription.updated", "customer.subscription.deleted" ->
                    synchronizeStripeSubscription(object, event.getType());
            default -> {
                // Stripe will retry only failed deliveries. Ignoring unrelated signed events is intentional.
            }
        }
    }

    @Override
    @Transactional
    public void initializeTrial(Store store) {
        if (store == null || store.getId() == null || subscriptionRepository.existsByStoreId(store.getId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(StoreSubscription.builder()
                .store(store)
                .plan(SubscriptionPlan.FREE)
                .status(SubscriptionStatus.TRIALING)
                .trialStart(now)
                .trialEnd(now.plusDays(TRIAL_DAYS))
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plusDays(TRIAL_DAYS))
                .build());
    }

    @Override
    @Transactional
    public void deleteForStore(Store store) {
        if (store == null || store.getId() == null) {
            return;
        }
        subscriptionRepository.findByStoreId(store.getId()).ifPresent(subscriptionRepository::delete);
    }

    @Override
    @Transactional
    public void assertCanCreateBranch(Store store) {
        ensureSubscriptionUsable(requireSubscription(store));
    }

    @Override
    @Transactional
    public void assertCanCreateEmployee(Store store) {
        // Employee accounts are not limited by subscription plan. We still
        // require an active/trial subscription so blocked stores cannot write.
        ensureSubscriptionUsable(requireSubscription(store));
    }

    @Override
    @Transactional
    public void assertCanCreateProduct(Store store) {
        ensureSubscriptionUsable(requireSubscription(store));
    }

    private void ensureSubscriptionUsable(StoreSubscription subscription) {
        LocalDateTime now = LocalDateTime.now();
        if (subscription.getStatus() == SubscriptionStatus.TRIALING
                && subscription.getTrialEnd() != null && now.isAfter(subscription.getTrialEnd())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            throw ApiException.forbidden("Gói dùng thử đã hết hạn");
        }
        if (subscription.getStatus() != SubscriptionStatus.TRIALING && subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw ApiException.forbidden("Subscription hiện không cho phép tạo dữ liệu mới: " + subscription.getStatus());
        }
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getCurrentPeriodEnd() != null && now.isAfter(subscription.getCurrentPeriodEnd())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            throw ApiException.forbidden("Subscription đã hết hạn");
        }
    }

    private StoreSubscription requireSubscription(Store store) {
        return subscriptionRepository.findByStoreId(store.getId())
                .orElseThrow(() -> ApiException.conflict("Cửa hàng chưa có subscription hợp lệ"));
    }

    private Store requireStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private Store storeFor(User user) {
        if (user.getStore() != null) {
            return user.getStore();
        }
        if (user.getBranch() != null && user.getBranch().getStore() != null) {
            return user.getBranch().getStore();
        }
        Store ownedStore = storeRepository.findByStoreAdminId(user.getId());
        if (ownedStore != null) {
            return ownedStore;
        }
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private void requireReadAccess(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.ROLE_STORE_ADMIN && store.getStoreAdmin() != null
                && Objects.equals(store.getStoreAdmin().getId(), user.getId())) {
            return;
        }
        if (!Objects.equals(storeFor(user).getId(), store.getId())) {
            throw ApiException.forbidden("Không có quyền xem subscription của cửa hàng này");
        }
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.forbidden("Chỉ quản trị viên hệ thống có thể quản trị subscription");
        }
    }

    private void requireStoreAdmin(User user) {
        if (user.getRole() != UserRole.ROLE_STORE_ADMIN) {
            throw ApiException.forbidden("Chỉ chủ cửa hàng có thể quản lý thanh toán Stripe");
        }
    }

    private void configureStripe() {
        if (!stripeProperties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Stripe Billing đang tắt. Đặt STRIPE_ENABLED=true sau khi cấu hình credentials.");
        }
        if (isBlank(stripeProperties.secretKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thiếu STRIPE_SECRET_KEY");
        }
        Stripe.apiKey = stripeProperties.secretKey();
    }

    private String priceIdFor(SubscriptionPlan plan) {
        String priceId = switch (plan) {
            case BASIC -> stripeProperties.basicPriceId();
            case PRO -> stripeProperties.proPriceId();
            case FREE -> null;
        };
        if (isBlank(priceId)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình Stripe Price ID cho gói " + plan.name());
        }
        return priceId;
    }

    private String frontendUrl() {
        if (isBlank(stripeProperties.appUrl())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thiếu STRIPE_APP_URL");
        }
        return stripeProperties.appUrl().replaceAll("/+$", "");
    }

    private String ensureStripeCustomer(StoreSubscription subscription, Store store, User owner) {
        if (!isBlank(subscription.getStripeCustomerId())) {
            return subscription.getStripeCustomerId();
        }
        try {
            CustomerCreateParams.Builder customerParams = CustomerCreateParams.builder()
                    .setEmail(owner.getEmail())
                    .setName(owner.getFullName())
                    .putMetadata("store_id", store.getId().toString());
            if (!isBlank(owner.getPhone())) {
                customerParams.setPhone(owner.getPhone());
            }
            Customer customer = Customer.create(customerParams.build());
            if (isBlank(customer.getId())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe không trả về customer ID");
            }
            subscription.setStripeCustomerId(customer.getId());
            subscriptionRepository.save(subscription);
            return customer.getId();
        } catch (StripeException exception) {
            throw stripeFailure(exception);
        }
    }

    private ApiException stripeFailure(StripeException exception) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Không thể kết nối Stripe. Hãy kiểm tra cấu hình Billing.");
    }

    private void synchronizeCheckout(JsonObject session) {
        Long storeId = asLong(session, "client_reference_id");
        if (storeId == null) {
            storeId = asLong(metadata(session), "store_id");
        }
        if (storeId == null) {
            return;
        }
        subscriptionRepository.findByStoreId(storeId).ifPresent(subscription -> {
            String customerId = asString(session, "customer");
            String subscriptionId = asString(session, "subscription");
            if (!isBlank(customerId)) {
                subscription.setStripeCustomerId(customerId);
            }
            if (!isBlank(subscriptionId)) {
                subscription.setStripeSubscriptionId(subscriptionId);
            }
            SubscriptionPlan plan = planFrom(asString(metadata(session), "plan"));
            if (plan != null && plan != SubscriptionPlan.FREE) {
                subscription.setPlan(plan);
            }
            subscriptionRepository.save(subscription);
        });
    }

    private void synchronizeStripeSubscription(JsonObject stripeSubscription, String eventType) {
        String subscriptionId = asString(stripeSubscription, "id");
        String customerId = asString(stripeSubscription, "customer");
        StoreSubscription subscription = subscriptionRepository.findByStripeSubscriptionId(subscriptionId)
                .or(() -> subscriptionRepository.findByStripeCustomerId(customerId))
                .orElseGet(() -> subscriptionForStoreId(asLong(metadata(stripeSubscription), "store_id")));
        if (subscription == null) {
            return;
        }
        if (!isBlank(customerId)) {
            subscription.setStripeCustomerId(customerId);
        }
        if (!isBlank(subscriptionId)) {
            subscription.setStripeSubscriptionId(subscriptionId);
        }
        SubscriptionPlan plan = planFrom(asString(metadata(stripeSubscription), "plan"));
        if (plan == null) {
            plan = planFromPrice(stripeSubscription);
        }
        if (plan != null && plan != SubscriptionPlan.FREE) {
            subscription.setPlan(plan);
        }
        subscription.setStatus(statusFromStripe(asString(stripeSubscription, "status"), eventType));
        LocalDateTime periodStart = asDateTime(stripeSubscription, "current_period_start");
        LocalDateTime periodEnd = asDateTime(stripeSubscription, "current_period_end");
        if (periodStart != null) {
            subscription.setCurrentPeriodStart(periodStart);
        }
        if (periodEnd != null) {
            subscription.setCurrentPeriodEnd(periodEnd);
        }
        subscriptionRepository.save(subscription);
    }

    private StoreSubscription subscriptionForStoreId(Long storeId) {
        return storeId == null ? null : subscriptionRepository.findByStoreId(storeId).orElse(null);
    }

    private SubscriptionPlan planFromPrice(JsonObject stripeSubscription) {
        JsonObject items = objectAt(stripeSubscription, "items");
        JsonArray data = items == null ? null : arrayAt(items, "data");
        if (data == null || data.isEmpty()) {
            return null;
        }
        JsonObject item = data.get(0).isJsonObject() ? data.get(0).getAsJsonObject() : null;
        JsonObject price = item == null ? null : objectAt(item, "price");
        String priceId = price == null ? null : asString(price, "id");
        if (Objects.equals(priceId, stripeProperties.basicPriceId())) {
            return SubscriptionPlan.BASIC;
        }
        if (Objects.equals(priceId, stripeProperties.proPriceId())) {
            return SubscriptionPlan.PRO;
        }
        return null;
    }

    private SubscriptionPlan planFrom(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return SubscriptionPlan.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SubscriptionStatus statusFromStripe(String stripeStatus, String eventType) {
        if ("customer.subscription.deleted".equals(eventType)) {
            return SubscriptionStatus.CANCELED;
        }
        return switch (stripeStatus == null ? "" : stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "incomplete_expired" -> SubscriptionStatus.EXPIRED;
            default -> SubscriptionStatus.PAST_DUE;
        };
    }

    private JsonObject webhookObject(String payload) {
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            JsonObject data = objectAt(root, "data");
            return data == null ? null : objectAt(data, "object");
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("Stripe webhook không chứa JSON hợp lệ");
        }
    }

    private JsonObject metadata(JsonObject object) {
        return object == null ? null : objectAt(object, "metadata");
    }

    private JsonObject objectAt(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonArray arrayAt(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private String asString(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        return element != null && !element.isJsonNull() && element.isJsonPrimitive()
                ? element.getAsString() : null;
    }

    private Long asLong(JsonObject object, String name) {
        String value = asString(object, name);
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDateTime asDateTime(JsonObject object, String name) {
        JsonElement element = object == null ? null : object.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(element.getAsLong()), ZoneId.systemDefault());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }
}
