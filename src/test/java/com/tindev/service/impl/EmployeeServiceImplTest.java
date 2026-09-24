package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.UserDto;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.UserService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    void createBranchManagerHashesPasswordAndAssignsTheManager() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store store = store(10L, owner);
        owner.setStore(store);
        Branch branch = branch(20L, store);
        UserDto request = employeeRequest(UserRole.ROLE_BRANCH_MANAGER);
        request.setBranchId(branch.getId());
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(userService.getCurrentUser()).thenReturn(owner);
        when(userRepository.findByEmail("manager@example.com")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        employeeService.createBranchEmployee(request, branch.getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals(store, saved.getStore());
        assertEquals(branch, saved.getBranch());
        assertEquals("hashed-password", saved.getPassword());
        assertEquals(saved, branch.getManager());
        verify(branchRepository).save(branch);
    }

    @Test
    void branchManagerCannotCreateAnotherBranchManager() throws Exception {
        Store store = store(10L, user(1L, UserRole.ROLE_STORE_ADMIN));
        Branch branch = branch(20L, store);
        User manager = user(2L, UserRole.ROLE_BRANCH_MANAGER);
        manager.setStore(store);
        manager.setBranch(branch);
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(userService.getCurrentUser()).thenReturn(manager);

        ApiException exception = assertThrows(ApiException.class,
                () -> employeeService.createBranchEmployee(employeeRequest(UserRole.ROLE_BRANCH_MANAGER), branch.getId()));

        assertEquals(403, exception.status().value());
    }

    @Test
    void updateEmployeeKeepsExistingPasswordWhenNoNewPasswordIsSent() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store store = store(10L, owner);
        owner.setStore(store);
        Branch branch = branch(20L, store);
        User existing = user(99L, UserRole.ROLE_BRANCH_CASHIER);
        existing.setStore(store);
        existing.setBranch(branch);
        existing.setFullName("Thu ngân cũ");
        existing.setEmail("cashier@example.com");
        existing.setPassword("existing-hash");
        UserDto update = employeeRequest(UserRole.ROLE_BRANCH_CASHIER);
        update.setEmail(existing.getEmail());
        update.setPassword(null);
        update.setBranchId(null);
        update.setStoreId(store.getId());
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(userService.getCurrentUser()).thenReturn(owner);
        when(userRepository.findByEmail(existing.getEmail())).thenReturn(existing);
        when(userRepository.save(existing)).thenReturn(existing);

        employeeService.updateEmployee(existing.getId(), update);

        assertEquals("existing-hash", existing.getPassword());
        assertEquals(branch, existing.getBranch());
        verify(passwordEncoder, never()).encode(any());
        assertTrue(existing.getUpdatedAt() != null);
    }

    @Test
    void systemAdminCanCreateASecondaryStoreAdminForTheSelectedStore() throws Exception {
        User systemAdmin = user(1L, UserRole.ROLE_ADMIN);
        Store store = store(10L, user(2L, UserRole.ROLE_STORE_ADMIN));
        UserDto request = employeeRequest(UserRole.ROLE_STORE_ADMIN);
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(userService.getCurrentUser()).thenReturn(systemAdmin);
        when(userRepository.findByEmail("manager@example.com")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        employeeService.createStoreEmployee(request, store.getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.ROLE_STORE_ADMIN, userCaptor.getValue().getRole());
        assertEquals(store, userCaptor.getValue().getStore());
    }

    @Test
    void storeAdminCanSeePeerAccountsAssignedToTheSameStore() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store store = store(10L, owner);
        owner.setStore(store);
        User peer = user(2L, UserRole.ROLE_STORE_ADMIN);
        peer.setStore(store);
        peer.setFullName("Quản trị viên thứ hai");
        peer.setEmail("peer@example.com");
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(userService.getCurrentUser()).thenReturn(owner);
        when(userRepository.findByStore(store)).thenReturn(List.of(owner, peer));

        List<UserDto> result = employeeService.findStoreEmployees(store.getId(), null);

        assertEquals(2, result.size());
        assertEquals(UserRole.ROLE_STORE_ADMIN, result.get(1).getRole());
    }

    @Test
    void storeManagerCanReadOnlyPersonnelFromTheAssignedStore() throws Exception {
        Store store = store(10L, user(1L, UserRole.ROLE_STORE_ADMIN));
        User manager = user(2L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);
        manager.setFullName("Quản lý cửa hàng");
        manager.setEmail("store-manager@example.com");
        User cashier = user(3L, UserRole.ROLE_BRANCH_CASHIER);
        cashier.setStore(store);
        cashier.setFullName("Thu ngân");
        cashier.setEmail("cashier@example.com");
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(userService.getCurrentUser()).thenReturn(manager);
        when(userRepository.findByStore(store)).thenReturn(List.of(manager, cashier));

        assertEquals(2, employeeService.findStoreEmployees(store.getId(), null).size());

        Store foreignStore = store(99L, user(9L, UserRole.ROLE_STORE_ADMIN));
        when(storeRepository.findById(foreignStore.getId())).thenReturn(Optional.of(foreignStore));
        ApiException exception = assertThrows(ApiException.class,
                () -> employeeService.findStoreEmployees(foreignStore.getId(), null));
        assertEquals(403, exception.status().value());
    }

    private UserDto employeeRequest(UserRole role) {
        UserDto request = new UserDto();
        request.setFullName("Nguyễn Văn A");
        request.setEmail("manager@example.com");
        request.setPhone("0900000000");
        request.setRole(role);
        request.setPassword("password123");
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
