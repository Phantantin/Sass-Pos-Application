package com.tindev.controller;

import com.tindev.payload.dto.InventoryDTO;
import com.tindev.payload.dto.InventoryMovementDTO;
import com.tindev.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/inventories")
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER')")
    public ResponseEntity<InventoryDTO> createInventory(@Valid @RequestBody InventoryDTO inventoryDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createInventory(inventoryDTO));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER')")
    public ResponseEntity<InventoryDTO> updateInventory(@PathVariable Long id,
                                                        @Valid @RequestBody InventoryDTO inventoryDTO) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, inventoryDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER')")
    public ResponseEntity<Void> deleteInventory(@PathVariable Long id,
                                                @RequestParam @NotBlank(message = "Lý do xóa tồn kho là bắt buộc") String reason) {
        inventoryService.deleteInventory(id, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<InventoryDTO> getInventoryById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getInventoryById(id));
    }

    @GetMapping("/branch/{branchId}/product/{productId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<InventoryDTO> getInventoryByProductAndBranchId(@PathVariable Long branchId,
                                                                          @PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductIdAndBranchId(productId, branchId));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<List<InventoryDTO>> getInventoryByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(inventoryService.getAllInventoryByBranchId(branchId));
    }

    @GetMapping("/branch/{branchId}/movements")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<List<InventoryMovementDTO>> getMovementHistory(@PathVariable Long branchId,
                                                                          @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(inventoryService.getMovementHistory(branchId, productId));
    }
}
