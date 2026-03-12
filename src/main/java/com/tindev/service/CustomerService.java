package com.tindev.service;

import com.tindev.modal.Customer;

import java.util.List;

public interface CustomerService {

    Customer createCustomer(Customer customer);
    Customer updateCustomer(Long id, Customer customer)throws Exception;
    void deleteCustomer(Long id)throws Exception;
    Customer getCustomer(Long id)throws Exception;
    List<Customer> getAllCustomers()throws Exception;
    List<Customer> searchCustomer(String keyword)throws Exception;
}
