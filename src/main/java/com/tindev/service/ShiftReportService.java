package com.tindev.service;

import com.tindev.payload.dto.ShiftReportDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ShiftReportService {
    ShiftReportDTO startShift() throws Exception;
    ShiftReportDTO endShift() throws Exception;
    ShiftReportDTO getShiftReportById(Long id);
    List<ShiftReportDTO> getAllShiftReports();
    List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId);
    List<ShiftReportDTO> getShiftReportsByCashierId(Long cashierId);
    ShiftReportDTO getCurrentShiftProgress() throws Exception;
    ShiftReportDTO getShiftCashierAndDate(Long cashierId, LocalDateTime date);
}
