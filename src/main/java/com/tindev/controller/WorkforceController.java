package com.tindev.controller;

import com.tindev.payload.dto.AttendanceDTO;
import com.tindev.payload.dto.PayrollDTO;
import com.tindev.payload.dto.WorkScheduleDTO;
import com.tindev.service.WorkforceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workforce")
@PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER', 'BRANCH_CASHIER')")
public class WorkforceController {
    private final WorkforceService workforceService;

    @GetMapping("/schedules")
    public List<WorkScheduleDTO> schedules(@RequestParam(required = false) Long storeId, @RequestParam(required = false) Long branchId,
                                           @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return workforceService.schedules(storeId, branchId, from, to);
    }

    @PostMapping("/schedules")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<WorkScheduleDTO> createSchedule(@Valid @RequestBody WorkScheduleDTO request) {
        return ResponseEntity.status(201).body(workforceService.saveSchedule(null, request));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public WorkScheduleDTO updateSchedule(@PathVariable Long id, @Valid @RequestBody WorkScheduleDTO request) {
        return workforceService.saveSchedule(id, request);
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        workforceService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance")
    public List<AttendanceDTO> attendance(@RequestParam(required = false) Long storeId, @RequestParam(required = false) Long branchId,
                                          @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return workforceService.attendance(storeId, branchId, from, to);
    }

    @PostMapping("/attendance/check-in")
    public AttendanceDTO checkIn(@Valid @RequestBody AttendanceDTO request) { return workforceService.checkIn(request); }

    @PostMapping("/attendance/check-out")
    public AttendanceDTO checkOut() { return workforceService.checkOut(); }

    @PutMapping("/attendance/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public AttendanceDTO updateAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceDTO request) {
        return workforceService.updateAttendance(id, request);
    }

    @GetMapping("/payrolls")
    public List<PayrollDTO> payrolls(@RequestParam(required = false) Long storeId, @RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to) {
        return workforceService.payrolls(storeId, from, to);
    }

    @PostMapping("/payrolls")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER')")
    public ResponseEntity<PayrollDTO> createPayroll(@Valid @RequestBody PayrollDTO request) {
        return ResponseEntity.status(201).body(workforceService.savePayroll(null, request));
    }

    @PutMapping("/payrolls/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER')")
    public PayrollDTO updatePayroll(@PathVariable Long id, @Valid @RequestBody PayrollDTO request) {
        return workforceService.savePayroll(id, request);
    }

    @PostMapping("/payrolls/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public PayrollDTO pay(@PathVariable Long id) { return workforceService.pay(id); }
}
