package com.tindev.repository;

import com.tindev.modal.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    List<WorkSchedule> findByBranchStoreIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(Long storeId, LocalDate from, LocalDate to);
    List<WorkSchedule> findByBranchIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(Long branchId, LocalDate from, LocalDate to);
    List<WorkSchedule> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDescStartTimeAsc(Long employeeId, LocalDate from, LocalDate to);
}
