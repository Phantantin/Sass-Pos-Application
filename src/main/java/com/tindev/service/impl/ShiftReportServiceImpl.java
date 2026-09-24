package com.tindev.service.impl;

import com.tindev.domain.PaymentType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.ShiftReportMapper;
import com.tindev.modal.Order;
import com.tindev.modal.OrderItem;
import com.tindev.modal.PaymentSummary;
import com.tindev.modal.Product;
import com.tindev.modal.Refund;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.User;
import com.tindev.payload.dto.ShiftReportDTO;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.ShiftReportRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.ShiftReportService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShiftReportServiceImpl implements ShiftReportService {
    private final ShiftReportRepository shiftReportRepository;
    private final UserService userService;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ShiftReportDTO startShift() throws Exception {
        User cashier = userService.getCurrentUser();
        if (cashier.getBranch() == null) {
            throw ApiException.forbidden("Tài khoản chưa được gán chi nhánh");
        }
        if (shiftReportRepository.findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier).isPresent()) {
            throw ApiException.conflict("Bạn đang có một ca làm việc mở");
        }
        ShiftReport report = ShiftReport.builder().cashier(cashier).branch(cashier.getBranch())
                .shiftStart(LocalDateTime.now()).totalSales(BigDecimal.ZERO).totalRefund(BigDecimal.ZERO)
                .netSale(BigDecimal.ZERO).totalOrder(0).build();
        return ShiftReportMapper.toDTO(shiftReportRepository.save(report));
    }

    @Override
    @Transactional
    public ShiftReportDTO endShift() throws Exception {
        User cashier = userService.getCurrentUser();
        ShiftReport report = activeShift(cashier);
        report.setShiftEnd(LocalDateTime.now());
        populateMetrics(report, report.getShiftEnd());
        return ShiftReportMapper.toDTO(shiftReportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getShiftReportById(Long id) {
        ShiftReport report = shiftReportRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo cáo ca"));
        requireShiftAccess(currentUser(), report);
        return ShiftReportMapper.toDTO(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getAllShiftReports() {
        User current = currentUser();
        return shiftReportRepository.findAll().stream().filter(report -> hasShiftAccess(current, report))
                .map(ShiftReportMapper::toDTO).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId) {
        User current = currentUser();
        return shiftReportRepository.findByBranchId(branchId).stream().filter(report -> hasShiftAccess(current, report))
                .map(ShiftReportMapper::toDTO).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByCashierId(Long cashierId) {
        User current = currentUser();
        return shiftReportRepository.findByCashierId(cashierId).stream().filter(report -> hasShiftAccess(current, report))
                .map(ShiftReportMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getCurrentShiftProgress() throws Exception {
        ShiftReport report = activeShift(userService.getCurrentUser());
        populateMetrics(report, LocalDateTime.now());
        return ShiftReportMapper.toDTO(report);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getShiftCashierAndDate(Long cashierId, LocalDateTime date) {
        User cashier = userRepository.findById(cashierId).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhân viên"));
        LocalDateTime start = date.toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        ShiftReport report = shiftReportRepository.findByCashierAndShiftStartBetween(cashier, start, end)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo cáo ca trong ngày đã chọn"));
        requireShiftAccess(currentUser(), report);
        return ShiftReportMapper.toDTO(report);
    }

    private ShiftReport activeShift(User cashier) {
        return shiftReportRepository.findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .orElseThrow(() -> ApiException.conflict("Không có ca làm việc đang mở"));
    }

    private void populateMetrics(ShiftReport report, LocalDateTime end) {
        List<Order> orders = orderRepository.findByCashierAndCreatedAtBetween(report.getCashier(), report.getShiftStart(), end);
        List<Refund> refunds = refundRepository.findByCashierIdAndCreatedAtBetween(report.getCashier().getId(), report.getShiftStart(), end);
        BigDecimal sales = orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundsTotal = refunds.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.setTotalSales(sales);
        report.setTotalRefund(refundsTotal);
        report.setNetSale(sales.subtract(refundsTotal));
        report.setTotalOrder(orders.size());
        report.setRecentOrders(orders.stream().sorted(Comparator.comparing(Order::getCreatedAt).reversed()).limit(5).toList());
        report.setTopSellingProducts(topProducts(orders));
        report.setPaymentSummaries(paymentSummaries(orders, sales));
    }

    private List<PaymentSummary> paymentSummaries(List<Order> orders, BigDecimal totalSales) {
        Map<PaymentType, List<Order>> grouped = new EnumMap<>(PaymentType.class);
        for (Order order : orders) {
            grouped.computeIfAbsent(order.getPaymentType(), ignored -> new ArrayList<>()).add(order);
        }
        List<PaymentSummary> result = new ArrayList<>();
        for (Map.Entry<PaymentType, List<Order>> entry : grouped.entrySet()) {
            BigDecimal amount = entry.getValue().stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            PaymentSummary summary = new PaymentSummary();
            summary.setPaymentType(entry.getKey());
            summary.setTotalAmount(amount);
            summary.setTransactionCount(entry.getValue().size());
            summary.setPercentage(totalSales.signum() == 0 ? 0d : amount.multiply(BigDecimal.valueOf(100))
                    .divide(totalSales, 2, RoundingMode.HALF_UP).doubleValue());
            result.add(summary);
        }
        return result;
    }

    private List<Product> topProducts(List<Order> orders) {
        Map<Product, Integer> quantities = new HashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                quantities.merge(item.getProduct(), item.getQuantity(), Integer::sum);
            }
        }
        return quantities.entrySet().stream().sorted(Map.Entry.<Product, Integer>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).toList();
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }

    private void requireShiftAccess(User user, ShiftReport report) {
        if (!hasShiftAccess(user, report)) {
            throw ApiException.forbidden("Bạn không có quyền xem báo cáo ca này");
        }
    }

    private boolean hasShiftAccess(User user, ShiftReport report) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }
        if (report.getBranch() == null || report.getBranch().getStore() == null) {
            return false;
        }
        Long storeId = user.getStore() != null ? user.getStore().getId()
                : user.getBranch() != null && user.getBranch().getStore() != null ? user.getBranch().getStore().getId() : null;
        boolean ownedStore = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && report.getBranch().getStore().getStoreAdmin() != null
                && java.util.Objects.equals(report.getBranch().getStore().getStoreAdmin().getId(), user.getId());
        if (!ownedStore && !java.util.Objects.equals(storeId, report.getBranch().getStore().getId())) {
            return false;
        }
        boolean branchScoped = user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                || user.getRole() == UserRole.ROLE_BRANCH_CASHIER;
        return !branchScoped || (user.getBranch() != null
                && java.util.Objects.equals(user.getBranch().getId(), report.getBranch().getId()));
    }
}
