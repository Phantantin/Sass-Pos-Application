package com.tindev.controller;

import com.tindev.exceptions.UserException;
import com.tindev.modal.Branch;
import com.tindev.payload.dto.BranchDTO;
import com.tindev.payload.response.ApiResponse;
import com.tindev.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService branchService;

    @PostMapping
    public ResponseEntity<BranchDTO> createBranch(@RequestBody BranchDTO branchDTO) throws UserException {
        BranchDTO createdBranch = branchService.createBranch(branchDTO);
        return ResponseEntity.ok(createdBranch);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchDTO> getBranchById(
            @PathVariable Long id
    ) throws Exception {
        BranchDTO getBranchById = branchService.getBranchById(id);
        return ResponseEntity.ok(getBranchById);
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<BranchDTO>> getAllBranchesByStoreId(
            @PathVariable Long storeId
    ) throws Exception {
        List<BranchDTO> getAllBranchByStoreId = branchService.getAllBranchesByStoreId(storeId);
        return ResponseEntity.ok(getAllBranchByStoreId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> updateBranch(
            @PathVariable Long id,
            @RequestBody BranchDTO branchDTO
    ) throws Exception {
        BranchDTO updateBranch = branchService.updateBranch(id, branchDTO);
        return ResponseEntity.ok(updateBranch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteBranch(
            @PathVariable Long id
    ) throws Exception {
        branchService.deleteBranch(id);
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Branch deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}
