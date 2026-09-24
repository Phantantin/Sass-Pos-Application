package com.tindev.repository;

import com.tindev.modal.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email
    );

    List<Customer> findByStoreId(Long storeId);
    List<Customer> findByStoreIdAndFullNameContainingIgnoreCaseOrStoreIdAndEmailContainingIgnoreCase(
            Long storeId, String fullName, Long duplicateStoreId, String email
    );

}
