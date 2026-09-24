package com.tindev.repository;

import com.tindev.modal.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByBranchStoreIdAndWorkDateBetweenOrderByWorkDateDesc(Long storeId, LocalDate from, LocalDate to);
    List<Attendance> findByBranchIdAndWorkDateBetweenOrderByWorkDateDesc(Long branchId, LocalDate from, LocalDate to);
    List<Attendance> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(Long employeeId, LocalDate from, LocalDate to);
    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
}
