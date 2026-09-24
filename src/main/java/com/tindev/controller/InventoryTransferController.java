package com.tindev.controller;

import com.tindev.payload.dto.InventoryTransferCreateRequest;
import com.tindev.payload.dto.InventoryTransferDTO;
import com.tindev.payload.dto.InventoryTransferReviewRequest;
import com.tindev.payload.dto.TransferAvailabilityDTO;
import com.tindev.service.InventoryTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory-transfers")
@PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'STORE_MANAGER', 'BRANCH_MANAGER')")
public class InventoryTransferController {
    private final InventoryTransferService transferService;

    @GetMapping("/availability")
    public ResponseEntity<List<TransferAvailabilityDTO>> availability(@RequestParam Long productId,
                                                                       @RequestParam Long destinationBranchId) {
        return ResponseEntity.ok(transferService.findAvailability(productId, destinationBranchId));
    }

    @GetMapping
    public ResponseEntity<List<InventoryTransferDTO>> list() {
        return ResponseEntity.ok(transferService.getVisibleRequests());
    }

    @PostMapping
    public ResponseEntity<InventoryTransferDTO> create(@Valid @RequestBody InventoryTransferCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.create(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InventoryTransferDTO> approve(@PathVariable Long id,
                                                         @Valid @RequestBody(required = false) InventoryTransferReviewRequest request) {
        return ResponseEntity.ok(transferService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<InventoryTransferDTO> reject(@PathVariable Long id,
                                                        @Valid @RequestBody(required = false) InventoryTransferReviewRequest request) {
        return ResponseEntity.ok(transferService.reject(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<InventoryTransferDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.cancel(id));
    }
}
