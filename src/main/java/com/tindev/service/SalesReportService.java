package com.tindev.service;

import com.tindev.payload.dto.SalesReportDTO;

import java.time.LocalDate;

public interface SalesReportService {
    SalesReportDTO getSalesReport(LocalDate from, LocalDate to, Long branchId, Long storeId);

    default SalesReportDTO getSalesReport(LocalDate from, LocalDate to, Long branchId) {
        return getSalesReport(from, to, branchId, null);
    }
}
