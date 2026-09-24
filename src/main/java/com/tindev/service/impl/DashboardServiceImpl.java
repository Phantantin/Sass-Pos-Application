package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.OrderMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.Order;
import com.tindev.modal.Refund;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.DashboardOverviewDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.DashboardService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final UserService userService;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewDTO getOverview() {
        User user = currentUser();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        Scope scope = resolveScope(user);

        List<Order> orders = scope.global()
                ? orderRepository.findByCreatedAtBetween(start, end)
                : scope.branches().stream().flatMap(branch -> orderRepository
                        .findByBranchIdAndCreatedAtBetween(branch.getId(), start, end).stream()).toList();
        List<Refund> refunds = scope.global()
                ? refundRepository.findByCreatedAtBetween(start, end)
                : scope.branches().stream().flatMap(branch -> refundRepository
                        .findByBranchIdAndCreatedAtBetween(branch.getId(), start, end).stream()).toList();
        List<Inventory> inventory = scope.global()
                ? inventoryRepository.findAll()
                : scope.branches().stream().flatMap(branch -> inventoryRepository.findByBranchId(branch.getId()).stream()).toList();

        BigDecimal sales = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundTotal = refunds.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return DashboardOverviewDTO.builder()
                .storeId(scope.storeId())
                .branchId(scope.branchId())
                .salesToday(sales)
                .refundsToday(refundTotal)
                .netSalesToday(sales.subtract(refundTotal))
                .ordersToday(orders.size())
                .lowStockItems(inventory.stream().filter(item -> item.getQuantity()
                        <= (item.getMinStockLevel() == null ? 5 : item.getMinStockLevel())).count())
                .recentOrders(orders.stream().sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                        .limit(5).map(OrderMapper::toDTO).toList())
                .build();
    }

    private Scope resolveScope(User user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return new Scope(null, null, List.of(), true);
        }
        if (user.getRole() == UserRole.ROLE_BRANCH_CASHIER || user.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            if (user.getBranch() == null) {
                throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh");
            }
            return new Scope(user.getBranch().getStore().getId(), user.getBranch().getId(), List.of(user.getBranch()), false);
        }
        Store store = user.getStore() != null ? user.getStore() : storeRepository.findByStoreAdminId(user.getId());
        if (store == null) {
            throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
        }
        return new Scope(store.getId(), null, branchRepository.findByStoreId(store.getId()), false);
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }

    private record Scope(Long storeId, Long branchId, List<Branch> branches, boolean global) {
    }
}
