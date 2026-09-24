package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTenantIsolationTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserService userService;
    @InjectMocks private InventoryServiceImpl inventoryService;

    @Test
    void systemAdminCanReadAnyBranchInventory() throws Exception {
        Branch foreignBranch = branch(20L, store(2L));
        when(userService.getCurrentUser()).thenReturn(user(1L, UserRole.ROLE_ADMIN, null, null));
        when(branchRepository.findById(foreignBranch.getId())).thenReturn(java.util.Optional.of(foreignBranch));
        when(inventoryRepository.findByBranchId(foreignBranch.getId())).thenReturn(List.of(inventory(foreignBranch)));

        assertEquals(1, inventoryService.getAllInventoryByBranchId(foreignBranch.getId()).size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("readersForAssignedBranch")
    void tenantRolesCanReadOnlyTheirAssignedStoreOrBranch(User actor) throws Exception {
        Branch allowedBranch = branch(10L, actor.getStore() != null ? actor.getStore() : actor.getBranch().getStore());
        if (actor.getBranch() != null) actor.setBranch(allowedBranch);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(branchRepository.findById(allowedBranch.getId())).thenReturn(java.util.Optional.of(allowedBranch));
        when(inventoryRepository.findByBranchId(allowedBranch.getId())).thenReturn(List.of(inventory(allowedBranch)));

        assertEquals(1, inventoryService.getAllInventoryByBranchId(allowedBranch.getId()).size());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("nonAdminTenantRoles")
    void tenantRolesCannotReadAnotherStoresInventory(User actor) throws Exception {
        Branch foreignBranch = branch(20L, store(99L));
        when(userService.getCurrentUser()).thenReturn(actor);
        when(branchRepository.findById(foreignBranch.getId())).thenReturn(java.util.Optional.of(foreignBranch));

        ApiException exception = assertThrows(ApiException.class,
                () -> inventoryService.getAllInventoryByBranchId(foreignBranch.getId()));

        assertEquals(403, exception.status().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("rolesAndTheirInventoryWriteAccess")
    void everyRoleHasExpectedWriteAccessInsideItsOwnScope(User actor, boolean allowed) throws Exception {
        Store store = actor.getStore() != null ? actor.getStore()
                : actor.getBranch() != null ? actor.getBranch().getStore() : store(1L);
        Branch scopedBranch = branch(10L, store);
        if (actor.getBranch() != null) actor.setBranch(scopedBranch);
        Inventory existing = inventory(scopedBranch);
        InventoryDTO request = new InventoryDTO();
        request.setQuantity(2);
        request.setReason("Kiểm thử tenant write access");
        when(userService.getCurrentUser()).thenReturn(actor);
        when(inventoryRepository.findById(existing.getId())).thenReturn(java.util.Optional.of(existing));

        if (allowed) {
            when(inventoryRepository.save(existing)).thenReturn(existing);
            assertEquals(2, inventoryService.updateInventory(existing.getId(), request).getQuantity());
        } else {
            ApiException exception = assertThrows(ApiException.class,
                    () -> inventoryService.updateInventory(existing.getId(), request));
            assertEquals(403, exception.status().value());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("nonAdminTenantRoles")
    void tenantRolesCannotMutateAnotherStoresInventory(User actor) throws Exception {
        Inventory foreignInventory = inventory(branch(20L, store(99L)));
        InventoryDTO request = new InventoryDTO();
        request.setQuantity(2);
        request.setReason("Không được phép");
        when(userService.getCurrentUser()).thenReturn(actor);
        when(inventoryRepository.findById(foreignInventory.getId())).thenReturn(java.util.Optional.of(foreignInventory));

        ApiException exception = assertThrows(ApiException.class,
                () -> inventoryService.updateInventory(foreignInventory.getId(), request));

        assertEquals(403, exception.status().value());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> readersForAssignedBranch() {
        Store store = store(1L);
        return Stream.of(
                arguments(user(1L, UserRole.ROLE_STORE_ADMIN, store, null)),
                arguments(user(2L, UserRole.ROLE_STORE_MANAGER, store, null)),
                arguments(user(3L, UserRole.ROLE_BRANCH_MANAGER, null, branch(10L, store))),
                arguments(user(4L, UserRole.ROLE_BRANCH_CASHIER, null, branch(10L, store))));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> nonAdminTenantRoles() {
        Store store = store(1L);
        return Stream.of(
                arguments(user(1L, UserRole.ROLE_STORE_ADMIN, store, null)),
                arguments(user(2L, UserRole.ROLE_STORE_MANAGER, store, null)),
                arguments(user(3L, UserRole.ROLE_BRANCH_MANAGER, null, branch(10L, store))),
                arguments(user(4L, UserRole.ROLE_BRANCH_CASHIER, null, branch(10L, store))));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> rolesAndTheirInventoryWriteAccess() {
        Store store = store(1L);
        return Stream.of(
                arguments(user(1L, UserRole.ROLE_ADMIN, null, null), true),
                arguments(user(2L, UserRole.ROLE_STORE_ADMIN, store, null), true),
                arguments(user(3L, UserRole.ROLE_STORE_MANAGER, store, null), true),
                arguments(user(4L, UserRole.ROLE_BRANCH_MANAGER, null, branch(10L, store)), true),
                arguments(user(5L, UserRole.ROLE_BRANCH_CASHIER, null, branch(10L, store)), false));
    }

    private static Inventory inventory(Branch branch) {
        Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setBranch(branch);
        inventory.setProduct(product(branch.getStore()));
        inventory.setQuantity(1);
        return inventory;
    }

    private static Product product(Store store) {
        Product product = new Product();
        product.setId(1L);
        product.setStore(store);
        product.setName("Sản phẩm test");
        product.setSku("TENANT-TEST");
        return product;
    }

    private static Branch branch(Long id, Store store) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setStore(store);
        return branch;
    }

    private static Store store(Long id) {
        Store store = new Store();
        store.setId(id);
        return store;
    }

    private static User user(Long id, UserRole role, Store store, Branch branch) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStore(store);
        user.setBranch(branch);
        if (role == UserRole.ROLE_STORE_ADMIN && store != null) store.setStoreAdmin(user);
        return user;
    }
}
