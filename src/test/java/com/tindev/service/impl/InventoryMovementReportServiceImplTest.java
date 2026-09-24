package com.tindev.service.impl;

import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.InventoryMovement;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryMovementReportDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryMovementReportServiceImplTest {

    @Mock
    private UserService userService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @InjectMocks
    private InventoryMovementReportServiceImpl inventoryMovementReportService;

    @Test
    void aggregatesSignedMovementsOnlyWithinTheCurrentStoreScope() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = from.plusDays(1);
        Store store = store(10L);
        Branch firstBranch = branch(20L, store);
        Branch secondBranch = branch(21L, store);
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);
        Product coffee = product(30L, "Cà phê", "CF-01");
        Product tea = product(31L, "Trà đào", "TD-01");

        InventoryMovement initialStock = movement(firstBranch, coffee, InventoryMovementType.INITIAL_STOCK,
                10, from.atTime(0, 0));
        InventoryMovement sale = movement(firstBranch, coffee, InventoryMovementType.SALE,
                -4, from.atTime(23, 59, 59));
        InventoryMovement adjustment = movement(secondBranch, tea, InventoryMovementType.ADJUSTMENT,
                2, to.atTime(12, 0));
        InventoryMovement removal = movement(secondBranch, tea, InventoryMovementType.REMOVAL,
                0, to.atTime(16, 0));

        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findByStoreId(store.getId())).thenReturn(List.of(firstBranch, secondBranch));
        when(inventoryMovementRepository.findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(firstBranch.getId()), any(), any())).thenReturn(List.of(initialStock, sale));
        when(inventoryMovementRepository.findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(secondBranch.getId()), any(), any())).thenReturn(List.of(adjustment, removal));

        InventoryMovementReportDTO report = inventoryMovementReportService
                .getInventoryMovementReport(from, to, null);

        assertEquals(store.getId(), report.getStoreId());
        assertEquals(null, report.getBranchId());
        assertEquals(4, report.getMovementCount());
        assertEquals(12, report.getInboundQuantity());
        assertEquals(4, report.getOutboundQuantity());
        assertEquals(8, report.getNetQuantity());
        assertEquals(2, report.getDailyMovements().get(0).getMovementCount());
        assertEquals(6, report.getDailyMovements().get(0).getNetQuantity());
        assertEquals(2, report.getDailyMovements().get(1).getMovementCount());
        assertEquals(2, report.getDailyMovements().get(1).getNetQuantity());
        assertEquals(4, report.getTypeBreakdown().size());
        assertEquals(InventoryMovementType.INITIAL_STOCK, report.getTypeBreakdown().get(0).getType());
        assertEquals(InventoryMovementType.ADJUSTMENT, report.getTypeBreakdown().get(1).getType());
        assertEquals(14, report.getTopProducts().get(0).getMovementVolume());
        assertEquals("Cà phê", report.getTopProducts().get(0).getProductName());
        assertEquals(6, report.getTopProducts().get(0).getNetQuantity());

        verify(inventoryMovementRepository)
                .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        firstBranch.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        verify(inventoryMovementRepository)
                .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        secondBranch.getId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    @Test
    void narrowsAStoreReportToTheRequestedBranch() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 1);
        Store store = store(10L);
        Branch firstBranch = branch(20L, store);
        Branch requestedBranch = branch(21L, store);
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);
        InventoryMovement refund = movement(requestedBranch, product(31L, "Trà đào", "TD-01"),
                InventoryMovementType.REFUND, 3, date.atTime(10, 0));

        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findByStoreId(store.getId())).thenReturn(List.of(firstBranch, requestedBranch));
        when(branchRepository.findById(requestedBranch.getId())).thenReturn(Optional.of(requestedBranch));
        when(inventoryMovementRepository.findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(requestedBranch.getId()), any(), any())).thenReturn(List.of(refund));

        InventoryMovementReportDTO report = inventoryMovementReportService
                .getInventoryMovementReport(date, date, requestedBranch.getId());

        assertEquals(store.getId(), report.getStoreId());
        assertEquals(requestedBranch.getId(), report.getBranchId());
        assertEquals(3, report.getInboundQuantity());
        verify(inventoryMovementRepository, never())
                .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        eq(firstBranch.getId()), any(), any());
        verify(inventoryMovementRepository)
                .findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        requestedBranch.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Test
    void branchManagerCannotRequestAnotherBranchesInventoryReport() throws Exception {
        Store store = store(10L);
        Branch assignedBranch = branch(20L, store);
        Branch otherBranch = branch(21L, store);
        User manager = user(2L, UserRole.ROLE_BRANCH_MANAGER);
        manager.setBranch(assignedBranch);
        when(userService.getCurrentUser()).thenReturn(manager);
        when(branchRepository.findById(otherBranch.getId())).thenReturn(Optional.of(otherBranch));

        ApiException exception = assertThrows(ApiException.class,
                () -> inventoryMovementReportService.getInventoryMovementReport(
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), otherBranch.getId()));

        assertEquals(403, exception.status().value());
    }

    @Test
    void platformAdminUsesTheExplicitCrossStoreReportingScope() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 1);
        Branch branch = branch(20L, store(10L));
        User platformAdmin = user(99L, UserRole.ROLE_ADMIN);
        InventoryMovement sale = movement(branch, product(30L, "Cà phê", "CF-01"),
                InventoryMovementType.SALE, -2, date.atTime(10, 0));

        when(userService.getCurrentUser()).thenReturn(platformAdmin);
        when(inventoryMovementRepository.findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay())).thenReturn(List.of(sale));

        InventoryMovementReportDTO report = inventoryMovementReportService
                .getInventoryMovementReport(date, date, null);

        assertEquals(null, report.getStoreId());
        assertEquals(2, report.getOutboundQuantity());
        assertEquals(-2, report.getNetQuantity());
        verify(inventoryMovementRepository)
                .findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Test
    void rejectsAnInvalidDateRangeBeforeLoadingAnyTenantData() {
        LocalDate from = LocalDate.of(2026, 9, 2);
        LocalDate to = LocalDate.of(2026, 9, 1);

        ApiException exception = assertThrows(ApiException.class,
                () -> inventoryMovementReportService.getInventoryMovementReport(from, to, null));

        assertEquals(400, exception.status().value());
        verify(inventoryMovementRepository, never())
                .findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any());
    }

    private InventoryMovement movement(Branch branch, Product product, InventoryMovementType type,
                                       int delta, LocalDateTime createdAt) {
        InventoryMovement movement = new InventoryMovement();
        movement.setBranch(branch);
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantityBefore(0);
        movement.setQuantityAfter(delta);
        movement.setQuantityDelta(delta);
        movement.setCreatedAt(createdAt);
        return movement;
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

    private Product product(Long id, String name, String sku) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        return product;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
