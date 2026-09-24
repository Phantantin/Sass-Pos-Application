package com.tindev.service;

import com.tindev.domain.UserRole;
import com.tindev.modal.User;
import com.tindev.payload.dto.UserDto;

import java.util.List;

public interface EmployeeService {

    UserDto createStoreEmployee(UserDto employee, Long storeId);
    UserDto createBranchEmployee(UserDto employee, Long branchId);
    UserDto updateEmployee(Long employeeId, UserDto employeeDetails);
    void deleteEmployee(Long employeeId);
    List<UserDto> findStoreEmployees(Long storeId, UserRole role);
    List<UserDto> findBranchEmployees(Long branchId, UserRole role);
}
