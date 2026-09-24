package com.tindev.service.impl;

import com.tindev.domain.AttendanceStatus;
import com.tindev.domain.UserRole;
import com.tindev.modal.Attendance;
import com.tindev.modal.Branch;
import com.tindev.modal.Payroll;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.PayrollDTO;
import com.tindev.repository.AttendanceRepository;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.PayrollRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.repository.WorkScheduleRepository;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkforceServiceImplTest {
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private WorkScheduleRepository scheduleRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private PayrollRepository payrollRepository;
    @InjectMocks private WorkforceServiceImpl workforceService;

    @Test
    void calculatesHourlyPayrollFromCompletedPresentAndLateAttendance() throws Exception {
        Store store = new Store();
        store.setId(10L);
        store.setBrand("Store A");
        Branch branch = new Branch();
        branch.setId(20L);
        branch.setStore(store);

        User manager = user(1L, "Manager", UserRole.ROLE_STORE_MANAGER, store, null);
        User employee = user(2L, "Cashier", UserRole.ROLE_BRANCH_CASHIER, store, branch);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        List<Attendance> records = List.of(
                attendance(employee, branch, from, AttendanceStatus.PRESENT, 8),
                attendance(employee, branch, from.plusDays(1), AttendanceStatus.LATE, 4),
                attendance(employee, branch, from.plusDays(2), AttendanceStatus.ABSENT, 5));

        PayrollDTO request = new PayrollDTO();
        request.setEmployeeId(employee.getId());
        request.setPeriodStart(from);
        request.setPeriodEnd(to);
        request.setBaseSalary(BigDecimal.ZERO);
        request.setHourlyRate(new BigDecimal("120000"));
        request.setBonus(new BigDecimal("10000"));
        request.setDeduction(new BigDecimal("5000"));

        when(userService.getCurrentUser()).thenReturn(manager);
        when(userRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(employee.getId(), from, to))
                .thenReturn(records);
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(invocation -> {
            Payroll payroll = invocation.getArgument(0);
            payroll.setId(99L);
            return payroll;
        });

        PayrollDTO result = workforceService.savePayroll(null, request);

        assertThat(result.getWorkedMinutes()).isEqualTo(720);
        assertThat(result.getHourlyRate()).isEqualByComparingTo("120000");
        assertThat(result.getBaseSalary()).isEqualByComparingTo("1440000.00");
        assertThat(result.getNetSalary()).isEqualByComparingTo("1445000.00");
    }

    @Test
    void cashierOnlyReadsOwnPayrollRecords() throws Exception {
        Store store = new Store();
        store.setId(10L);
        User cashier = user(2L, "Cashier", UserRole.ROLE_BRANCH_CASHIER, store, null);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        when(userService.getCurrentUser()).thenReturn(cashier);
        when(payrollRepository.findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(
                cashier.getId(), from, to)).thenReturn(List.of());

        assertThat(workforceService.payrolls(999L, from, to)).isEmpty();

        verify(payrollRepository)
                .findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(
                        cashier.getId(), from, to);
    }

    private User user(Long id, String name, UserRole role, Store store, Branch branch) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setRole(role);
        user.setStore(store);
        user.setBranch(branch);
        return user;
    }

    private Attendance attendance(User employee, Branch branch, LocalDate date, AttendanceStatus status, int hours) {
        LocalDateTime checkIn = date.atTime(8, 0);
        return Attendance.builder()
                .id(date.toEpochDay())
                .employee(employee)
                .branch(branch)
                .workDate(date)
                .checkIn(checkIn)
                .checkOut(checkIn.plusHours(hours))
                .status(status)
                .build();
    }
}
