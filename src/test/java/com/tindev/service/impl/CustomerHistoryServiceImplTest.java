package com.tindev.service.impl;

import com.tindev.domain.OrderStatus;
import com.tindev.domain.PaymentType;
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
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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
class CustomerHistoryServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private CustomerHistoryServiceImpl customerHistoryService;

    @Test
    void storeManagerGetsPaginatedHistoryAndServerCalculatedRefunds() throws Exception {
        Store store = store(10L);
        Customer customer = customer(30L, store);
        Branch branch = branch(20L, store, "Chi nhánh Quận 1");
        Order order = order(100L, branch, "150000");
        Refund refund = refund(200L, order, "40000");
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(store);

        when(userService.getCurrentUser()).thenReturn(manager);
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(eq(customer.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));
        when(refundRepository.findByOrderIdIn(List.of(order.getId()))).thenReturn(List.of(refund));

        CustomerHistoryDTO result = customerHistoryService.getCustomerHistory(customer.getId(), 0, 20);

        assertEquals(customer.getId(), result.getCustomerId());
        assertEquals(1, result.getTotalOrders());
        assertEquals(1, result.getOrders().size());
        CustomerHistoryDTO.OrderHistoryItem item = result.getOrders().get(0);
        assertEquals(branch.getId(), item.getBranchId());
        assertEquals("Chi nhánh Quận 1", item.getBranchName());
        assertEquals(new BigDecimal("150000"), item.getTotalAmount());
        assertEquals(new BigDecimal("40000"), item.getRefundedAmount());
        assertEquals(new BigDecimal("110000"), item.getNetAmount());
        verify(orderRepository, never()).findByCustomerIdAndBranchIdOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void branchCashierCanOnlyReadOrdersFromAssignedBranch() throws Exception {
        Store store = store(10L);
        Customer customer = customer(30L, store);
        Branch branch = branch(20L, store, "Chi nhánh Quận 1");
        User cashier = user(1L, UserRole.ROLE_BRANCH_CASHIER);
        cashier.setBranch(branch);

        when(userService.getCurrentUser()).thenReturn(cashier);
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndBranchIdOrderByCreatedAtDesc(eq(customer.getId()), eq(branch.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        CustomerHistoryDTO result = customerHistoryService.getCustomerHistory(customer.getId(), 0, 20);

        assertEquals(0, result.getTotalOrders());
        verify(orderRepository).findByCustomerIdAndBranchIdOrderByCreatedAtDesc(eq(customer.getId()), eq(branch.getId()), any());
        verify(refundRepository, never()).findByOrderIdIn(any());
    }

    @Test
    void userFromAnotherStoreCannotReadCustomerHistory() throws Exception {
        Store customerStore = store(10L);
        Store otherStore = store(11L);
        Customer customer = customer(30L, customerStore);
        User manager = user(1L, UserRole.ROLE_STORE_MANAGER);
        manager.setStore(otherStore);

        when(userService.getCurrentUser()).thenReturn(manager);
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        ApiException exception = assertThrows(ApiException.class,
                () -> customerHistoryService.getCustomerHistory(customer.getId(), 0, 20));

        assertEquals(403, exception.status().value());
        verify(orderRepository, never()).findByCustomerIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void systemAdminCanReadHistoryWithoutStoreAssignment() throws Exception {
        Customer customer = customer(30L, store(10L));
        User admin = user(1L, UserRole.ROLE_ADMIN);

        when(userService.getCurrentUser()).thenReturn(admin);
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(eq(customer.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        CustomerHistoryDTO result = customerHistoryService.getCustomerHistory(customer.getId(), 0, 20);

        assertEquals(0, result.getTotalOrders());
        verify(storeRepository, never()).findByStoreAdminId(any());
    }

    @Test
    void rejectsInvalidPaginationBeforeAccessingData() {
        ApiException exception = assertThrows(ApiException.class,
                () -> customerHistoryService.getCustomerHistory(30L, 0, 101));

        assertEquals(400, exception.status().value());
        verify(customerRepository, never()).findById(any());
    }

    @Test
    void mapsMissingAuthenticatedUserToUnauthorized() throws Exception {
        Customer customer = customer(30L, store(10L));
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(userService.getCurrentUser()).thenThrow(new UserException("missing authentication"));

        ApiException exception = assertThrows(ApiException.class,
                () -> customerHistoryService.getCustomerHistory(customer.getId(), 0, 20));

        assertEquals(401, exception.status().value());
        verify(orderRepository, never()).findByCustomerIdOrderByCreatedAtDesc(any(), any());
    }

    private Store store(Long id) {
        Store store = new Store();
        store.setId(id);
        return store;
    }

    private Customer customer(Long id, Store store) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFullName("Khách hàng test");
        customer.setStore(store);
        return customer;
    }

    private Branch branch(Long id, Store store, String name) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setStore(store);
        branch.setName(name);
        return branch;
    }

    private Order order(Long id, Branch branch, String amount) {
        Order order = new Order();
        order.setId(id);
        order.setBranch(branch);
        order.setCreatedAt(LocalDateTime.of(2026, 9, 3, 9, 0));
        order.setPaymentType(PaymentType.CASH);
        order.setStatus(OrderStatus.PARTIALLY_REFUNDED);
        order.setTotalAmount(new BigDecimal(amount));
        return order;
    }

    private Refund refund(Long id, Order order, String amount) {
        Refund refund = new Refund();
        refund.setId(id);
        refund.setOrder(order);
        refund.setAmount(new BigDecimal(amount));
        return refund;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
