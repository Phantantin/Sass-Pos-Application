package com.tindev.controller;

import com.tindev.payload.dto.BranchDTO;
import com.tindev.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService branchService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<BranchDTO> createBranch(@Valid @RequestBody BranchDTO branchDTO) {
        BranchDTO createdBranch = branchService.createBranch(branchDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBranch);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BranchDTO> getBranchById(
            @PathVariable Long id
    ) {
        BranchDTO getBranchById = branchService.getBranchById(id);
        return ResponseEntity.ok(getBranchById);
    }

    @GetMapping("/store/{storeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BranchDTO>> getAllBranchesByStoreId(
            @PathVariable Long storeId
    ) {
        List<BranchDTO> getAllBranchByStoreId = branchService.getAllBranchesByStoreId(storeId);
        return ResponseEntity.ok(getAllBranchByStoreId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN', 'BRANCH_MANAGER')")
    public ResponseEntity<BranchDTO> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody BranchDTO branchDTO
    ) {
        BranchDTO updateBranch = branchService.updateBranch(id, branchDTO);
        return ResponseEntity.ok(updateBranch);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<Void> deleteBranch(
            @PathVariable Long id
    ) {
        branchService.deleteBranch(id);
        return ResponseEntity.noContent().build();
    }
}
