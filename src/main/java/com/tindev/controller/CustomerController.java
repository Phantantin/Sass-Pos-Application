package com.tindev.controller;


import com.tindev.modal.Customer;
import com.tindev.payload.dto.CustomerHistoryDTO;
import com.tindev.service.CustomerHistoryService;
import com.tindev.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerHistoryService customerHistoryService;

    @PostMapping()
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
    public ResponseEntity<Customer> create(@Valid @RequestBody Customer customer,
                                           @RequestParam(required = false) Long storeId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(customer, storeId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
    public ResponseEntity<Customer> update(
            @PathVariable Long id,
            @Valid @RequestBody Customer customer) throws Exception {
        return ResponseEntity.ok(customerService.updateCustomer(id, customer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id) throws Exception {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping()
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
    public ResponseEntity<List<Customer>> getAll(@RequestParam(required = false) Long storeId) throws Exception {
        return ResponseEntity.ok(customerService.getAllCustomers(storeId));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
    public ResponseEntity<List<Customer>> search(
        @RequestParam String q,
        @RequestParam(required = false) Long storeId) throws Exception {
        return ResponseEntity.ok(customerService.searchCustomer(q, storeId));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
    public ResponseEntity<CustomerHistoryDTO> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(customerHistoryService.getCustomerHistory(id, page, pageSize));
    }


}
