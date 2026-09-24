package com.tindev.service.impl;

import com.tindev.domain.PaymentType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.Branch;
import com.tindev.modal.Order;
import com.tindev.modal.OrderItem;
import com.tindev.modal.Refund;
import com.tindev.modal.RefundItem;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.SalesReportDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.ShiftReportRepository;
import com.tindev.service.SalesReportService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SalesReportServiceImpl implements SalesReportService {
    private static final int MAX_REPORT_DAYS = 366;

    private final UserService userService;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ShiftReportRepository shiftReportRepository;

    @Override
    @Transactional(readOnly = true)
    public SalesReportDTO getSalesReport(LocalDate requestedFrom, LocalDate requestedTo, Long requestedBranchId, Long requestedStoreId) {
        LocalDate to = requestedTo == null ? LocalDate.now() : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(6) : requestedFrom;
        validateRange(from, to);

        User actor = currentUser();
        Scope scope = narrowToRequestedStore(resolveScope(actor), requestedStoreId, actor);
        scope = narrowToRequestedBranch(scope, requestedBranchId, actor);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        List<Order> orders = loadOrders(scope, start, endExclusive);
        List<Refund> refunds = loadRefunds(scope, start, endExclusive);
        List<ShiftReport> shifts = loadShifts(scope, start, endExclusive);

        FinancialTotals financials = financialTotals(orders, refunds);
        return SalesReportDTO.builder()
                .from(from)
                .to(to)
                .storeId(scope.storeId())
                .branchId(scope.branchId())
                .grossSales(financials.grossSales())
                .refunds(financials.refunds())
                .netSales(financials.netSales())
                .costOfGoodsSold(financials.costOfGoodsSold())
                .refundedCost(financials.refundedCost())
                .netCost(financials.netCost())
                .netProfit(financials.netProfit())
                .averageOrderValue(orders.isEmpty()
                        ? BigDecimal.ZERO
                        : financials.grossSales().divide(BigDecimal.valueOf(orders.size()), 2, java.math.RoundingMode.HALF_UP))
                .orderCount(orders.size())
                .dailySales(buildDailySales(from, to, orders, refunds))
                .paymentBreakdown(buildPaymentBreakdown(orders))
                .topProducts(buildTopProducts(orders, refunds))
                .branchPerformance(buildBranchPerformance(orders, refunds))
                .storePerformance(buildStorePerformance(orders, refunds))
                .shiftReports(buildShiftReports(shifts))
                .employeePerformance(buildEmployeePerformance(shifts))
                .build();
    }

    private List<Order> loadOrders(Scope scope, LocalDateTime start, LocalDateTime endExclusive) {
        if (scope.global()) {
            return orderRepository.findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive);
        }
        return scope.branches().stream()
                .flatMap(branch -> orderRepository
                        .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                branch.getId(), start, endExclusive)
                        .stream())
                .toList();
    }

    private List<Refund> loadRefunds(Scope scope, LocalDateTime start, LocalDateTime endExclusive) {
        if (scope.global()) {
            return refundRepository.findByCreatedAtBetween(start, endExclusive);
        }
        return scope.branches().stream()
                .flatMap(branch -> refundRepository.findByBranchIdAndCreatedAtBetween(branch.getId(), start, endExclusive).stream())
                .toList();
    }

    private List<ShiftReport> loadShifts(Scope scope, LocalDateTime start, LocalDateTime endExclusive) {
        if (scope.global()) {
            return shiftReportRepository.findByShiftStartGreaterThanEqualAndShiftStartLessThan(start, endExclusive);
        }
        return scope.branches().stream()
                .flatMap(branch -> shiftReportRepository
                        .findByBranchIdAndShiftStartGreaterThanEqualAndShiftStartLessThan(branch.getId(), start, endExclusive)
                        .stream())
                .toList();
    }

    private List<SalesReportDTO.DailySales> buildDailySales(
            LocalDate from, LocalDate to, List<Order> orders, List<Refund> refunds) {
        Map<LocalDate, BigDecimal> grossByDate = new HashMap<>();
        Map<LocalDate, Long> countByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> refundsByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> costByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> refundedCostByDate = new HashMap<>();
        for (Order order : orders) {
            LocalDate date = order.getCreatedAt().toLocalDate();
            grossByDate.merge(date, order.getTotalAmount(), BigDecimal::add);
            countByDate.merge(date, 1L, Long::sum);
            costByDate.merge(date, orderCost(order), BigDecimal::add);
        }
        for (Refund refund : refunds) {
            refundsByDate.merge(refund.getCreatedAt().toLocalDate(), refund.getAmount(), BigDecimal::add);
            refundedCostByDate.merge(refund.getCreatedAt().toLocalDate(), refundCost(refund), BigDecimal::add);
        }

        List<SalesReportDTO.DailySales> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            BigDecimal gross = grossByDate.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal refund = refundsByDate.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal netCost = costByDate.getOrDefault(date, BigDecimal.ZERO)
                    .subtract(refundedCostByDate.getOrDefault(date, BigDecimal.ZERO));
            result.add(SalesReportDTO.DailySales.builder()
                    .date(date)
                    .grossSales(gross)
                    .refunds(refund)
                    .netSales(gross.subtract(refund))
                    .netCost(netCost)
                    .netProfit(gross.subtract(refund).subtract(netCost))
                    .orderCount(countByDate.getOrDefault(date, 0L))
                    .build());
        }
        return result;
    }

    private List<SalesReportDTO.PaymentBreakdown> buildPaymentBreakdown(List<Order> orders) {
        Map<PaymentType, PaymentTotals> totals = new EnumMap<>(PaymentType.class);
        for (Order order : orders) {
            if (order.getPaymentType() == null) {
                continue;
            }
            totals.computeIfAbsent(order.getPaymentType(), ignored -> new PaymentTotals())
                    .add(order.getTotalAmount());
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> SalesReportDTO.PaymentBreakdown.builder()
                        .paymentType(entry.getKey())
                        .amount(entry.getValue().amount())
                        .orderCount(entry.getValue().count())
                        .build())
                .toList();
    }

    private List<SalesReportDTO.TopProduct> buildTopProducts(List<Order> orders, List<Refund> refunds) {
        Map<Long, ProductTotals> totals = new HashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null) {
                    continue;
                }
                totals.computeIfAbsent(item.getProduct().getId(), ignored -> new ProductTotals(
                                item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getSku()))
                        .addSale(item.getQuantity(), item.getPrice(), valueOrZero(item.getCostAmount()));
            }
        }
        for (Refund refund : refunds) {
            if (refund.getItems() == null) continue;
            for (RefundItem item : refund.getItems()) {
                if (item.getProduct() == null) continue;
                totals.computeIfAbsent(item.getProduct().getId(), ignored -> new ProductTotals(
                                item.getProduct().getId(), item.getProduct().getName(), item.getProduct().getSku()))
                        .addRefund(item.getQuantity(), item.getAmount(), valueOrZero(item.getCostAmount()));
            }
        }
        return totals.values().stream()
                .sorted(Comparator.comparing(ProductTotals::netProfit).reversed()
                        .thenComparing(ProductTotals::quantity, Comparator.reverseOrder()))
                .limit(10)
                .map(total -> SalesReportDTO.TopProduct.builder()
                        .productId(total.productId())
                        .productName(total.productName())
                        .sku(total.sku())
                        .quantity(total.quantity())
                        .grossSales(total.grossSales())
                        .refunds(total.refunds())
                        .netSales(total.netSales())
                        .netCost(total.netCost())
                        .netProfit(total.netProfit())
                        .build())
                .toList();
    }

    private List<SalesReportDTO.BranchPerformance> buildBranchPerformance(List<Order> orders, List<Refund> refunds) {
        Map<Long, ScopeFinancialTotals> totals = new HashMap<>();
        for (Order order : orders) {
            Branch branch = order.getBranch();
            if (branch == null || branch.getStore() == null) continue;
            totals.computeIfAbsent(branch.getId(), ignored -> ScopeFinancialTotals.forBranch(branch)).addOrder(order);
        }
        for (Refund refund : refunds) {
            Branch branch = refund.getBranch();
            if (branch == null || branch.getStore() == null) continue;
            totals.computeIfAbsent(branch.getId(), ignored -> ScopeFinancialTotals.forBranch(branch)).addRefund(refund);
        }
        return totals.values().stream().sorted(Comparator.comparing(ScopeFinancialTotals::netProfit).reversed())
                .map(total -> SalesReportDTO.BranchPerformance.builder()
                        .storeId(total.storeId).storeName(total.storeName)
                        .branchId(total.branchId).branchName(total.branchName)
                        .orderCount(total.orderCount).grossSales(total.grossSales).refunds(total.refunds)
                        .netSales(total.netSales()).netCost(total.netCost()).netProfit(total.netProfit()).build())
                .toList();
    }

    private List<SalesReportDTO.StorePerformance> buildStorePerformance(List<Order> orders, List<Refund> refunds) {
        Map<Long, ScopeFinancialTotals> totals = new HashMap<>();
        for (Order order : orders) {
            if (order.getBranch() == null || order.getBranch().getStore() == null) continue;
            Store store = order.getBranch().getStore();
            totals.computeIfAbsent(store.getId(), ignored -> ScopeFinancialTotals.forStore(store)).addOrder(order);
        }
        for (Refund refund : refunds) {
            if (refund.getBranch() == null || refund.getBranch().getStore() == null) continue;
            Store store = refund.getBranch().getStore();
            totals.computeIfAbsent(store.getId(), ignored -> ScopeFinancialTotals.forStore(store)).addRefund(refund);
        }
        return totals.values().stream().sorted(Comparator.comparing(ScopeFinancialTotals::netProfit).reversed())
                .map(total -> SalesReportDTO.StorePerformance.builder()
                        .storeId(total.storeId).storeName(total.storeName).orderCount(total.orderCount)
                        .grossSales(total.grossSales).refunds(total.refunds).netSales(total.netSales())
                        .netCost(total.netCost()).netProfit(total.netProfit()).build())
                .toList();
    }

    private FinancialTotals financialTotals(List<Order> orders, List<Refund> refunds) {
        BigDecimal grossSales = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundTotal = refunds.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = orders.stream().map(this::orderCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedCost = refunds.stream().map(this::refundCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinancialTotals(grossSales, refundTotal, cost, refundedCost);
    }

    private BigDecimal orderCost(Order order) {
        if (order.getItems() == null) return BigDecimal.ZERO;
        return order.getItems().stream().map(item -> valueOrZero(item.getCostAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal refundCost(Refund refund) {
        if (refund.getItems() == null) return BigDecimal.ZERO;
        return refund.getItems().stream().map(item -> valueOrZero(item.getCostAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<SalesReportDTO.ShiftSummary> buildShiftReports(List<ShiftReport> shifts) {
        return shifts.stream()
                .sorted(Comparator.comparing(ShiftReport::getShiftStart).reversed())
                .map(shift -> SalesReportDTO.ShiftSummary.builder()
                        .shiftId(shift.getId())
                        .branchId(shift.getBranch() == null ? null : shift.getBranch().getId())
                        .branchName(shift.getBranch() == null ? null : shift.getBranch().getName())
                        .cashierId(shift.getCashier() == null ? null : shift.getCashier().getId())
                        .cashierName(shift.getCashier() == null ? null : shift.getCashier().getFullName())
                        .shiftStart(shift.getShiftStart())
                        .shiftEnd(shift.getShiftEnd())
                        .totalSales(shift.getTotalSales())
                        .totalRefund(shift.getTotalRefund())
                        .netSale(shift.getNetSale())
                        .totalOrder(shift.getTotalOrder())
                        .build())
                .toList();
    }

    private List<SalesReportDTO.EmployeePerformance> buildEmployeePerformance(List<ShiftReport> shifts) {
        Map<Long, EmployeeTotals> totals = new HashMap<>();
        for (ShiftReport shift : shifts) {
            if (shift.getCashier() == null) {
                continue;
            }
            totals.computeIfAbsent(shift.getCashier().getId(), ignored -> new EmployeeTotals(
                    shift.getCashier().getId(), shift.getCashier().getFullName())).add(shift);
        }
        return totals.values().stream()
                .sorted(Comparator.comparing(EmployeeTotals::netSales).reversed())
                .map(total -> SalesReportDTO.EmployeePerformance.builder()
                        .employeeId(total.employeeId())
                        .employeeName(total.employeeName())
                        .shiftCount(total.shiftCount())
                        .orderCount(total.orderCount())
                        .grossSales(total.grossSales())
                        .refunds(total.refunds())
                        .netSales(total.netSales())
                        .averageOrderValue(total.orderCount() == 0 ? BigDecimal.ZERO
                                : total.grossSales().divide(BigDecimal.valueOf(total.orderCount()), 2,
                                        java.math.RoundingMode.HALF_UP))
                        .build())
                .toList();
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
            throw ApiException.forbidden("Không có quyền xem báo cáo của chi nhánh này");
        }
        return new Scope(scope.storeId(), branch.getId(), List.of(branch), false);
    }

    private Scope narrowToRequestedStore(Scope scope, Long requestedStoreId, User actor) {
        if (requestedStoreId == null) return scope;
        Store requested = storeRepository.findById(requestedStoreId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
        if (actor.getRole() == UserRole.ROLE_ADMIN) {
            return new Scope(requested.getId(), null, branchRepository.findByStoreId(requested.getId()), false);
        }
        if (actor.getRole() == UserRole.ROLE_STORE_ADMIN && requested.getStoreAdmin() != null
                && Objects.equals(requested.getStoreAdmin().getId(), actor.getId())) {
            return new Scope(requested.getId(), null, branchRepository.findByStoreId(requested.getId()), false);
        }
        if (!Objects.equals(scope.storeId(), requestedStoreId)) {
            throw ApiException.forbidden("Không có quyền xem báo cáo của cửa hàng này");
        }
        return scope;
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

    private record FinancialTotals(
            BigDecimal grossSales,
            BigDecimal refunds,
            BigDecimal costOfGoodsSold,
            BigDecimal refundedCost) {

        BigDecimal netSales() {
            return grossSales.subtract(refunds);
        }

        BigDecimal netCost() {
            return costOfGoodsSold.subtract(refundedCost);
        }

        BigDecimal netProfit() {
            return netSales().subtract(netCost());
        }
    }

    private static final class PaymentTotals {
        private BigDecimal amount = BigDecimal.ZERO;
        private long count;

        void add(BigDecimal value) {
            amount = amount.add(value);
            count++;
        }

        BigDecimal amount() {
            return amount;
        }

        long count() {
            return count;
        }
    }

    private static final class ProductTotals {
        private final Long productId;
        private final String productName;
        private final String sku;
        private long quantity;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal refunds = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private BigDecimal refundedCost = BigDecimal.ZERO;

        ProductTotals(Long productId, String productName, String sku) {
            this.productId = productId;
            this.productName = productName;
            this.sku = sku;
        }

        void addSale(Integer units, BigDecimal lineTotal, BigDecimal lineCost) {
            quantity += units;
            grossSales = grossSales.add(lineTotal);
            cost = cost.add(lineCost);
        }

        void addRefund(Integer units, BigDecimal amount, BigDecimal returnedCost) {
            quantity -= units;
            refunds = refunds.add(amount);
            refundedCost = refundedCost.add(returnedCost);
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

        long quantity() {
            return quantity;
        }

        BigDecimal grossSales() {
            return grossSales;
        }

        BigDecimal refunds() {
            return refunds;
        }

        BigDecimal netSales() {
            return grossSales.subtract(refunds);
        }

        BigDecimal netCost() {
            return cost.subtract(refundedCost);
        }

        BigDecimal netProfit() {
            return netSales().subtract(netCost());
        }
    }

    private static final class ScopeFinancialTotals {
        private Long storeId;
        private String storeName;
        private Long branchId;
        private String branchName;
        private long orderCount;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal refunds = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private BigDecimal refundedCost = BigDecimal.ZERO;

        static ScopeFinancialTotals forBranch(Branch branch) {
            ScopeFinancialTotals totals = new ScopeFinancialTotals();
            totals.storeId = branch.getStore().getId();
            totals.storeName = branch.getStore().getBrand();
            totals.branchId = branch.getId();
            totals.branchName = branch.getName();
            return totals;
        }

        static ScopeFinancialTotals forStore(Store store) {
            ScopeFinancialTotals totals = new ScopeFinancialTotals();
            totals.storeId = store.getId();
            totals.storeName = store.getBrand();
            return totals;
        }

        void addOrder(Order order) {
            orderCount++;
            grossSales = grossSales.add(zeroIfNull(order.getTotalAmount()));
            if (order.getItems() != null) {
                cost = cost.add(order.getItems().stream()
                        .map(item -> zeroIfNull(item.getCostAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }

        void addRefund(Refund refund) {
            refunds = refunds.add(zeroIfNull(refund.getAmount()));
            if (refund.getItems() != null) {
                refundedCost = refundedCost.add(refund.getItems().stream()
                        .map(item -> zeroIfNull(item.getCostAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }

        BigDecimal netSales() {
            return grossSales.subtract(refunds);
        }

        BigDecimal netCost() {
            return cost.subtract(refundedCost);
        }

        BigDecimal netProfit() {
            return netSales().subtract(netCost());
        }

        private static BigDecimal zeroIfNull(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private static final class EmployeeTotals {
        private final Long employeeId;
        private final String employeeName;
        private long shiftCount;
        private long orderCount;
        private BigDecimal grossSales = BigDecimal.ZERO;
        private BigDecimal refunds = BigDecimal.ZERO;
        private BigDecimal netSales = BigDecimal.ZERO;

        EmployeeTotals(Long employeeId, String employeeName) {
            this.employeeId = employeeId;
            this.employeeName = employeeName;
        }

        void add(ShiftReport shift) {
            shiftCount++;
            orderCount += shift.getTotalOrder();
            grossSales = grossSales.add(shift.getTotalSales());
            refunds = refunds.add(shift.getTotalRefund());
            netSales = netSales.add(shift.getNetSale());
        }

        Long employeeId() { return employeeId; }
        String employeeName() { return employeeName; }
        long shiftCount() { return shiftCount; }
        long orderCount() { return orderCount; }
        BigDecimal grossSales() { return grossSales; }
        BigDecimal refunds() { return refunds; }
        BigDecimal netSales() { return netSales; }
    }
}
