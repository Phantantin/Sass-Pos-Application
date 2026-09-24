package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.Branch;
import com.tindev.modal.Customer;
import com.tindev.modal.Order;
import com.tindev.modal.Refund;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.CustomerHistoryDTO;
import com.tindev.repository.CustomerRepository;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.CustomerHistoryService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerHistoryServiceImpl implements CustomerHistoryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public CustomerHistoryDTO getCustomerHistory(Long customerId, int page, int pageSize) {
        if (page < 0) {
            throw ApiException.badRequest("Số trang không được âm");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Kích thước trang phải từ 1 đến " + MAX_PAGE_SIZE);
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        Scope scope = resolveScope(currentUser());
        requireCustomerInScope(customer, scope);

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = scope.branchId() == null
                ? orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable)
                : orderRepository.findByCustomerIdAndBranchIdOrderByCreatedAtDesc(customer.getId(), scope.branchId(), pageable);
        Map<Long, BigDecimal> refundTotals = refundsByOrder(orders.getContent());

        return CustomerHistoryDTO.builder()
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .page(orders.getNumber())
                .pageSize(orders.getSize())
                .totalOrders(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .orders(orders.getContent().stream()
                        .map(order -> toHistoryItem(order, refundTotals.getOrDefault(order.getId(), BigDecimal.ZERO)))
                        .toList())
                .build();
    }

    private Map<Long, BigDecimal> refundsByOrder(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return refundRepository.findByOrderIdIn(orderIds).stream()
                .filter(refund -> refund.getOrder() != null && refund.getOrder().getId() != null)
                .collect(Collectors.groupingBy(
                        refund -> refund.getOrder().getId(),
                        Collectors.mapping(Refund::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    private CustomerHistoryDTO.OrderHistoryItem toHistoryItem(Order order, BigDecimal refundedAmount) {
        Branch branch = order.getBranch();
        BigDecimal totalAmount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        return CustomerHistoryDTO.OrderHistoryItem.builder()
                .orderId(order.getId())
                .branchId(branch == null ? null : branch.getId())
                .branchName(branch == null ? null : branch.getName())
                .createdAt(order.getCreatedAt())
                .paymentType(order.getPaymentType())
                .status(order.getStatus())
                .totalAmount(totalAmount)
                .refundedAmount(refundedAmount)
                .netAmount(totalAmount.subtract(refundedAmount))
                .build();
    }

    private void requireCustomerInScope(Customer customer, Scope scope) {
        if (scope.global()) {
            return;
        }
        if (customer.getStore() == null || !Objects.equals(customer.getStore().getId(), scope.storeId())) {
            throw ApiException.forbidden("Khách hàng không thuộc cửa hàng hiện tại");
        }
    }

    private Scope resolveScope(User user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return new Scope(null, null, true);
        }
        if (user.getRole() == UserRole.ROLE_BRANCH_MANAGER || user.getRole() == UserRole.ROLE_BRANCH_CASHIER) {
            Branch branch = user.getBranch();
            if (branch == null || branch.getStore() == null) {
                throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh hợp lệ");
            }
            return new Scope(branch.getStore().getId(), branch.getId(), false);
        }

        Store store = user.getStore() != null ? user.getStore() : storeRepository.findByStoreAdminId(user.getId());
        if (store == null) {
            throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
        }
        return new Scope(store.getId(), null, false);
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }

    private record Scope(Long storeId, Long branchId, boolean global) {
    }
}
