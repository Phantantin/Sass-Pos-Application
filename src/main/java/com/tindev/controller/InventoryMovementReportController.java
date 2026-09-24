package com.tindev.controller;

import com.tindev.payload.dto.InventoryMovementReportDTO;
import com.tindev.service.InventoryMovementReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class InventoryMovementReportController {
    private final InventoryMovementReportService inventoryMovementReportService;

    @GetMapping("/inventory-movements")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<InventoryMovementReportDTO> getInventoryMovementReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryMovementReportService.getInventoryMovementReport(from, to, branchId));
    }
}
