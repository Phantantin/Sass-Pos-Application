package com.tindev.service;

import com.tindev.payload.dto.InventoryMovementReportDTO;

import java.time.LocalDate;

public interface InventoryMovementReportService {
    InventoryMovementReportDTO getInventoryMovementReport(LocalDate from, LocalDate to, Long branchId);
}
