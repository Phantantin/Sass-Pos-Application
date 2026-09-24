package com.tindev.service;

import com.tindev.payload.dto.BranchDTO;

import java.util.List;

public interface BranchService {

    BranchDTO createBranch(BranchDTO branchDTO);
    BranchDTO updateBranch(Long id, BranchDTO branchDTO);
    void deleteBranch(Long id);
    List<BranchDTO> getAllBranchesByStoreId(Long storeId);
    BranchDTO getBranchById(Long id);
}
