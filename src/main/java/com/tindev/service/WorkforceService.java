package com.tindev.service;

import com.tindev.payload.dto.AttendanceDTO;
import com.tindev.payload.dto.PayrollDTO;
import com.tindev.payload.dto.WorkScheduleDTO;

import java.time.LocalDate;
import java.util.List;

public interface WorkforceService {
    List<WorkScheduleDTO> schedules(Long storeId, Long branchId, LocalDate from, LocalDate to);
    WorkScheduleDTO saveSchedule(Long id, WorkScheduleDTO request);
    void deleteSchedule(Long id);
    List<AttendanceDTO> attendance(Long storeId, Long branchId, LocalDate from, LocalDate to);
    AttendanceDTO checkIn(AttendanceDTO request);
    AttendanceDTO checkOut();
    AttendanceDTO updateAttendance(Long id, AttendanceDTO request);
    List<PayrollDTO> payrolls(Long storeId, LocalDate from, LocalDate to);
    PayrollDTO savePayroll(Long id, PayrollDTO request);
    PayrollDTO pay(Long id);
}
