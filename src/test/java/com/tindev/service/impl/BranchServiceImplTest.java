package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.BranchDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.UserService;
import com.tindev.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceImplTest {

    @Mock
    private BranchRepository branchRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private SubscriptionService subscriptionService;
    @InjectMocks
    private BranchServiceImpl branchService;

    @Test
    void createBranchUsesStoreOwnedByCurrentStoreAdmin() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store store = store(10L, owner);
        BranchDTO request = validBranchRequest();
        request.setStoreId(10L);
        when(userService.getCurrentUser()).thenReturn(owner);
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch branch = invocation.getArgument(0);
            branch.setId(100L);
            return branch;
        });

        BranchDTO created = branchService.createBranch(request);

        ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(branchCaptor.capture());
        Branch saved = branchCaptor.getValue();
        assertEquals(store, saved.getStore());
        assertEquals("Chi nhánh Quận 1", saved.getName());
        assertEquals(100L, created.getId());
        assertTrue(saved.getCreatedAt() != null);
    }

    @Test
    void createBranchRejectsStoreIdFromAnotherTenant() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        when(userService.getCurrentUser()).thenReturn(owner);
        BranchDTO request = validBranchRequest();
        request.setStoreId(20L);
        when(storeRepository.findById(20L)).thenReturn(Optional.of(store(20L, user(2L, UserRole.ROLE_STORE_ADMIN))));

        ApiException exception = assertThrows(ApiException.class, () -> branchService.createBranch(request));

        assertEquals(403, exception.status().value());
    }

    @Test
    void branchManagerCannotReadAnotherBranchInSameStore() throws Exception {
        Store store = store(10L, user(1L, UserRole.ROLE_STORE_ADMIN));
        Branch assignedBranch = branch(100L, store);
        Branch otherBranch = branch(200L, store);
        User manager = user(2L, UserRole.ROLE_BRANCH_MANAGER);
        manager.setBranch(assignedBranch);
        manager.setStore(store);
        when(branchRepository.findById(otherBranch.getId())).thenReturn(Optional.of(otherBranch));
        when(userService.getCurrentUser()).thenReturn(manager);

        ApiException exception = assertThrows(ApiException.class,
                () -> branchService.getBranchById(otherBranch.getId()));

        assertEquals(403, exception.status().value());
    }

    private BranchDTO validBranchRequest() {
        BranchDTO request = new BranchDTO();
        request.setName("Chi nhánh Quận 1");
        request.setAddress("1 Nguyễn Huệ");
        request.setPhone("0900000000");
        request.setEmail("branch@example.com");
        request.setWorkingDays(List.of("MONDAY", "TUESDAY"));
        request.setOpenTime(LocalTime.of(8, 0));
        request.setCloseTime(LocalTime.of(21, 0));
        return request;
    }

    private Store store(Long id, User owner) {
        Store store = new Store();
        store.setId(id);
        store.setStoreAdmin(owner);
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
