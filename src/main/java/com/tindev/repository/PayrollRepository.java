package com.tindev.repository;

import com.tindev.modal.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByStoreIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(Long storeId, LocalDate from, LocalDate to);
    List<Payroll> findByEmployeeIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodEndDesc(Long employeeId, LocalDate from, LocalDate to);
}
