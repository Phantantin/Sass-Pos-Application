package com.tindev.service.impl;

import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.Branch;
import com.tindev.modal.InventoryMovement;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryMovementReportDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.InventoryMovementReportService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds inventory movement reports from the append-only inventory audit trail.
 * All non-platform users are constrained to their own store (or assigned branch).
 */
@Service
@RequiredArgsConstructor
public class InventoryMovementReportServiceImpl implements InventoryMovementReportService {
    private static final int MAX_REPORT_DAYS = 366;
    private static final int TOP_PRODUCTS_LIMIT = 10;

    private final UserService userService;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final InventoryMovementRepository inventoryMovementRepository;

    @Override
    @Transactional(readOnly = true)
    public InventoryMovementReportDTO getInventoryMovementReport(
            LocalDate requestedFrom, LocalDate requestedTo, Long requestedBranchId) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(6) : requestedFrom;
        validateRange(from, to);

        User actor = currentUser();
        Scope scope = narrowToRequestedBranch(resolveScope(actor), requestedBranchId, actor);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        List<InventoryMovement> movements = loadMovements(scope, start, endExclusive);

        MovementTotals totals = new MovementTotals();
        Map<LocalDate, MovementTotals> dailyTotals = new HashMap<>();
        Map<InventoryMovementType, MovementTotals> typeTotals = new EnumMap<>(InventoryMovementType.class);
        Map<Long, ProductTotals> productTotals = new HashMap<>();
        for (InventoryMovement movement : movements) {
            long delta = movement.getQuantityDelta();
            totals.add(delta);
            dailyTotals.computeIfAbsent(movement.getCreatedAt().toLocalDate(), ignored -> new MovementTotals()).add(delta);
            typeTotals.computeIfAbsent(movement.getType(), ignored -> new MovementTotals()).add(delta);
            addProductMovement(productTotals, movement, delta);
        }

        return InventoryMovementReportDTO.builder()
                .from(from)
                .to(to)
                .storeId(scope.storeId())
                .branchId(scope.branchId())
                .movementCount(totals.movementCount())
                .inboundQuantity(totals.inboundQuantity())
                .outboundQuantity(totals.outboundQuantity())
                .netQuantity(totals.netQuantity())
                .dailyMovements(buildDailyMovements(from, to, dailyTotals))
                .typeBreakdown(buildTypeBreakdown(typeTotals))
                .topProducts(buildTopProducts(productTotals))
                .build();
    }

    private List<InventoryMovement> loadMovements(Scope scope, LocalDateTime start, LocalDateTime endExclusive) {
        if (scope.global()) {
            return inventoryMovementRepository.findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    start, endExclusive);
        }
        return scope.branches().stream()
                .flatMap(branch -> inventoryMovementRepository
                        .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                branch.getId(), start, endExclusive)
                        .stream())
                .toList();
    }

    private List<InventoryMovementReportDTO.DailyMovement> buildDailyMovements(
            LocalDate from, LocalDate to, Map<LocalDate, MovementTotals> dailyTotals) {
        List<InventoryMovementReportDTO.DailyMovement> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            MovementTotals totals = dailyTotals.getOrDefault(date, MovementTotals.EMPTY);
            result.add(InventoryMovementReportDTO.DailyMovement.builder()
                    .date(date)
                    .movementCount(totals.movementCount())
                    .inboundQuantity(totals.inboundQuantity())
                    .outboundQuantity(totals.outboundQuantity())
                    .netQuantity(totals.netQuantity())
                    .build());
        }
        return result;
    }

    private List<InventoryMovementReportDTO.MovementTypeBreakdown> buildTypeBreakdown(
            Map<InventoryMovementType, MovementTotals> typeTotals) {
        return typeTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> InventoryMovementReportDTO.MovementTypeBreakdown.builder()
                        .type(entry.getKey())
                        .movementCount(entry.getValue().movementCount())
                        .inboundQuantity(entry.getValue().inboundQuantity())
                        .outboundQuantity(entry.getValue().outboundQuantity())
                        .netQuantity(entry.getValue().netQuantity())
                        .build())
                .toList();
    }

    private List<InventoryMovementReportDTO.ProductMovement> buildTopProducts(
            Map<Long, ProductTotals> productTotals) {
        return productTotals.values().stream()
                .sorted(Comparator.comparing(ProductTotals::movementVolume).reversed()
                        .thenComparing(ProductTotals::productName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ProductTotals::productId))
                .limit(TOP_PRODUCTS_LIMIT)
                .map(total -> InventoryMovementReportDTO.ProductMovement.builder()
                        .productId(total.productId())
                        .productName(total.productName())
                        .sku(total.sku())
                        .movementCount(total.movementCount())
                        .inboundQuantity(total.inboundQuantity())
                        .outboundQuantity(total.outboundQuantity())
                        .netQuantity(total.netQuantity())
                        .movementVolume(total.movementVolume())
                        .build())
                .toList();
    }

    private void addProductMovement(Map<Long, ProductTotals> productTotals, InventoryMovement movement, long delta) {
        Product product = movement.getProduct();
        if (product == null || product.getId() == null) {
            return;
        }
        productTotals.computeIfAbsent(product.getId(), ignored -> new ProductTotals(
                        product.getId(), product.getName(), product.getSku()))
                .add(delta);
    }

    private Scope resolveScope(User user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return new Scope(null, null, List.of(), true);
        }
        if (user.getRole() == UserRole.ROLE_BRANCH_CASHIER || user.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            if (user.getBranch() == null) {
                throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh");
            }
            return new Scope(user.getBranch().getStore().getId(), user.getBranch().getId(),
                    List.of(user.getBranch()), false);
        }
        Store store = user.getStore() != null ? user.getStore() : storeRepository.findByStoreAdminId(user.getId());
        if (store == null) {
            throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
        }
        return new Scope(store.getId(), null, branchRepository.findByStoreId(store.getId()), false);
    }

    private Scope narrowToRequestedBranch(Scope scope, Long requestedBranchId, User actor) {
        if (requestedBranchId == null) {
            return scope;
        }
        Branch branch = branchRepository.findById(requestedBranchId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
        if (scope.global()) {
            return new Scope(branch.getStore().getId(), branch.getId(), List.of(branch), false);
        }
        if (actor.getRole() == UserRole.ROLE_STORE_ADMIN && branch.getStore().getStoreAdmin() != null
                && Objects.equals(branch.getStore().getStoreAdmin().getId(), actor.getId())) {
            return new Scope(branch.getStore().getId(), branch.getId(), List.of(branch), false);
        }
        boolean belongsToScope = scope.branches().stream()
                .anyMatch(allowed -> Objects.equals(allowed.getId(), branch.getId()));
        if (!belongsToScope) {
            throw ApiException.forbidden("Không có quyền xem báo cáo tồn kho của chi nhánh này");
        }
        return new Scope(scope.storeId(), branch.getId(), List.of(branch), false);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("Ngày bắt đầu không được sau ngày kết thúc");
        }
        if (from.plusDays(MAX_REPORT_DAYS - 1L).isBefore(to)) {
            throw ApiException.badRequest("Khoảng thời gian báo cáo tối đa là 366 ngày");
        }
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

    private static class MovementTotals {
        private static final MovementTotals EMPTY = new MovementTotals();

        private long movementCount;
        private long inboundQuantity;
        private long outboundQuantity;
        private long netQuantity;

        void add(long delta) {
            movementCount++;
            netQuantity += delta;
            if (delta >= 0) {
                inboundQuantity += delta;
            } else {
                outboundQuantity += Math.abs(delta);
            }
        }

        long movementCount() {
            return movementCount;
        }

        long inboundQuantity() {
            return inboundQuantity;
        }

        long outboundQuantity() {
            return outboundQuantity;
        }

        long netQuantity() {
            return netQuantity;
        }
    }

    private static final class ProductTotals extends MovementTotals {
        private final Long productId;
        private final String productName;
        private final String sku;

        ProductTotals(Long productId, String productName, String sku) {
            this.productId = productId;
            this.productName = productName;
            this.sku = sku;
        }

        long movementVolume() {
            return inboundQuantity() + outboundQuantity();
        }

        Long productId() {
            return productId;
        }

        String productName() {
            return productName;
        }

        String sku() {
            return sku;
        }
    }
}
