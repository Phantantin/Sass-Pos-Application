package com.tindev.service.impl;

import com.tindev.exceptions.ApiException;
import com.tindev.domain.UserRole;
import com.tindev.modal.Customer;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.repository.CustomerRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.CustomerService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    @Transactional
    public Customer createCustomer(Customer customer, Long storeId) {
        customer.setStore(currentStore(storeId));
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, Customer request) {
        Customer customer = ownedCustomer(id);
        customer.setFullName(request.getFullName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) { customerRepository.delete(ownedCustomer(id)); }

    @Override
    @Transactional(readOnly = true)
    public Customer getCustomer(Long id) { return ownedCustomer(id); }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers(Long storeId) {
        return customerRepository.findByStoreId(currentStore(storeId).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> searchCustomer(String keyword, Long requestedStoreId) {
        Long storeId = currentStore(requestedStoreId).getId();
        String query = keyword == null ? "" : keyword.trim();
        return customerRepository.findByStoreIdAndFullNameContainingIgnoreCaseOrStoreIdAndEmailContainingIgnoreCase(storeId, query, storeId, query);
    }

    private Customer ownedCustomer(Long id) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        requireStoreAccess(currentUser(), customer.getStore());
        return customer;
    }

    private Store currentStore(Long requestedStoreId) {
        User user = currentUser();
        if (requestedStoreId != null) {
            Store requested = storeRepository.findById(requestedStoreId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
            requireStoreAccess(user, requested);
            return requested;
        }
        if (user.getStore() != null) return user.getStore();
        if (user.getBranch() != null && user.getBranch().getStore() != null) return user.getBranch().getStore();
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (Exception exception) {
            throw ApiException.forbidden("Không thể xác thực tài khoản hiện tại");
        }
    }

    private void requireStoreAccess(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) return;
        if (user.getRole() == UserRole.ROLE_STORE_ADMIN && store.getStoreAdmin() != null
                && Objects.equals(store.getStoreAdmin().getId(), user.getId())) return;
        if (user.getStore() != null && Objects.equals(user.getStore().getId(), store.getId())) return;
        if (user.getBranch() != null && user.getBranch().getStore() != null
                && Objects.equals(user.getBranch().getStore().getId(), store.getId())) return;
        throw ApiException.forbidden("Khách hàng không thuộc phạm vi cửa hàng của tài khoản");
    }
}
