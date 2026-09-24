package com.tindev.service.impl;

import com.tindev.domain.PaymentType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Order;
import com.tindev.modal.OrderItem;
import com.tindev.modal.Product;
import com.tindev.modal.Refund;
import com.tindev.modal.Store;
import com.tindev.modal.ShiftReport;
import com.tindev.modal.User;
import com.tindev.payload.dto.SalesReportDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.OrderRepository;
import com.tindev.repository.RefundRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.ShiftReportRepository;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReportServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private ShiftReportRepository shiftReportRepository;
    @InjectMocks
    private SalesReportServiceImpl salesReportService;

    @Test
    void aggregatesOnlyOrdersAndRefundsWithinTheCurrentStoreScope() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        Store store = store(10L);
        Branch branch = branch(20L, store);
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);
        Order firstOrder = order(100L, branch, from.atTime(9, 0), PaymentType.CASH,
                item(product(50L, "Cà phê", "CF-01"), 2, "60000"));
        Order secondOrder = order(101L, branch, from.plusDays(1).atTime(10, 0), PaymentType.CARD,
                item(product(51L, "Trà đào", "TD-01"), 1, "40000"));
        Refund refund = refund(branch, from.plusDays(1).atTime(11, 0), "10000");

        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findByStoreId(store.getId())).thenReturn(List.of(branch));
        when(orderRepository.findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(branch.getId()), any(), any())).thenReturn(List.of(firstOrder, secondOrder));
        when(refundRepository.findByBranchIdAndCreatedAtBetween(eq(branch.getId()), any(), any()))
                .thenReturn(List.of(refund));
        when(shiftReportRepository.findByBranchIdAndShiftStartGreaterThanEqualAndShiftStartLessThan(
                eq(branch.getId()), any(), any())).thenReturn(List.of());

        SalesReportDTO report = salesReportService.getSalesReport(from, from.plusDays(1), null);

        assertEquals(new BigDecimal("100000"), report.getGrossSales());
        assertEquals(new BigDecimal("10000"), report.getRefunds());
        assertEquals(new BigDecimal("90000"), report.getNetSales());
        assertEquals(new BigDecimal("50000.00"), report.getAverageOrderValue());
        assertEquals(2, report.getOrderCount());
        assertEquals(new BigDecimal("60000"), report.getDailySales().get(0).getNetSales());
        assertEquals(new BigDecimal("30000"), report.getDailySales().get(1).getNetSales());
        assertEquals("Cà phê", report.getTopProducts().get(0).getProductName());
        assertEquals(2, report.getPaymentBreakdown().size());
    }

    @Test
    void branchManagerCannotRequestAReportForAnotherBranch() throws Exception {
        Store store = store(10L);
        Branch assignedBranch = branch(20L, store);
        Branch otherBranch = branch(21L, store);
        User manager = user(2L, UserRole.ROLE_BRANCH_MANAGER);
        manager.setBranch(assignedBranch);
        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findById(otherBranch.getId())).thenReturn(Optional.of(otherBranch));

        ApiException exception = assertThrows(ApiException.class,
                () -> salesReportService.getSalesReport(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), otherBranch.getId()));

        assertEquals(403, exception.status().value());
    }

    @Test
    void includesTenantScopedEmployeePerformanceAndShiftDetails() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 2);
        Store store = store(10L);
        Branch branch = branch(20L, store);
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);
        User cashier = user(4L, UserRole.ROLE_BRANCH_CASHIER);
        cashier.setFullName("Thu ngân A");
        ShiftReport shift = new ShiftReport();
        shift.setId(88L);
        shift.setBranch(branch);
        shift.setCashier(cashier);
        shift.setShiftStart(date.atTime(8, 0));
        shift.setShiftEnd(date.atTime(16, 0));
        shift.setTotalOrder(3);
        shift.setTotalSales(new BigDecimal("90000"));
        shift.setTotalRefund(new BigDecimal("10000"));
        shift.setNetSale(new BigDecimal("80000"));

        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findByStoreId(store.getId())).thenReturn(List.of(branch));
        when(orderRepository.findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(branch.getId()), any(), any())).thenReturn(List.of());
        when(refundRepository.findByBranchIdAndCreatedAtBetween(eq(branch.getId()), any(), any())).thenReturn(List.of());
        when(shiftReportRepository.findByBranchIdAndShiftStartGreaterThanEqualAndShiftStartLessThan(
                eq(branch.getId()), any(), any())).thenReturn(List.of(shift));

        SalesReportDTO report = salesReportService.getSalesReport(date, date, null);

        assertEquals(1, report.getShiftReports().size());
        assertEquals("Thu ngân A", report.getShiftReports().get(0).getCashierName());
        assertEquals(1, report.getEmployeePerformance().size());
        assertEquals(3, report.getEmployeePerformance().get(0).getOrderCount());
        assertEquals(new BigDecimal("80000"), report.getEmployeePerformance().get(0).getNetSales());
        assertEquals(new BigDecimal("30000.00"), report.getEmployeePerformance().get(0).getAverageOrderValue());
    }

    private Order order(Long id, Branch branch, LocalDateTime createdAt, PaymentType paymentType, OrderItem item) {
        Order order = new Order();
        order.setId(id);
        order.setBranch(branch);
        order.setCreatedAt(createdAt);
        order.setPaymentType(paymentType);
        order.setItems(List.of(item));
        item.setOrder(order);
        order.setTotalAmount(item.getPrice());
        return order;
    }

    private Refund refund(Branch branch, LocalDateTime createdAt, String amount) {
        Refund refund = new Refund();
        refund.setBranch(branch);
        refund.setCreatedAt(createdAt);
        refund.setAmount(new BigDecimal(amount));
        return refund;
    }

    private OrderItem item(Product product, int quantity, String lineTotal) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(lineTotal));
        return item;
    }

    private Product product(Long id, String name, String sku) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        return product;
    }

    private Store store(Long id) {
        Store store = new Store();
        store.setId(id);
        return store;
    }

    private Branch branch(Long id, Store store) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setStore(store);
        return branch;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
