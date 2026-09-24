package com.tindev.controller;

import com.tindev.domain.UserRole;
import com.tindev.payload.dto.UserDto;
import com.tindev.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees")
public class EmployeeController {
    private final EmployeeService employeeService;

    @PostMapping("/store/{storeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<UserDto> createStoreEmployee(
            @PathVariable Long storeId,
            @Valid @RequestBody UserDto userDto) {
        UserDto employee = employeeService.createStoreEmployee(userDto, storeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @PostMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<UserDto> createBranchEmployee(
            @PathVariable Long branchId,
            @Valid @RequestBody UserDto userDto) {
        UserDto employee = employeeService.createBranchEmployee(userDto, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(employee);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<UserDto> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UserDto userDto) {
        UserDto employee = employeeService.updateEmployee(id, userDto);
        return ResponseEntity.ok(employee);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/store/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER')")
    public ResponseEntity<List<UserDto>> storeEmployee(
            @PathVariable Long id,
            @RequestParam(required = false) UserRole userRole) {
        List<UserDto> employee = employeeService.findStoreEmployees(id, userRole);
        return ResponseEntity.ok(employee);
    }

    @GetMapping("/branch/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<List<UserDto>> branchEmployee(
            @PathVariable Long id,
            @RequestParam(required = false) UserRole userRole) {
        List<UserDto> employee = employeeService.findBranchEmployees(id, userRole);
        return ResponseEntity.ok(employee);
    }
}
