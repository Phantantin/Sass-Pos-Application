package com.tindev.service.impl;

import com.tindev.exceptions.UserException;
import com.tindev.mapper.BranchMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.BranchDTO;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.BranchRepository;
import com.tindev.service.BranchService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    public BranchDTO createBranch(BranchDTO branchDTO) throws UserException {
        User currrentUser = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(currrentUser.getId());
        Branch branch = BranchMapper.toEntity(branchDTO, store);
        Branch savedBranch = branchRepository.save(branch);

        return BranchMapper.toDTO(savedBranch);
    }

    @Override
    public BranchDTO updateBranch(Long id, BranchDTO branchDTO) throws Exception {
        Branch exiting = branchRepository.findById(id).orElseThrow(
                ()-> new Exception("Branch not exit...")
        );
        exiting.setName(branchDTO.getName());
        exiting.setWorkingDays(branchDTO.getWorkingDays());
        exiting.setEmail(branchDTO.getEmail());
        exiting.setPhone(branchDTO.getPhone());
        exiting.setAddress(branchDTO.getAddress());
        exiting.setOpenTime(branchDTO.getOpenTime());
        exiting.setCloseTime(branchDTO.getCloseTime());
        exiting.setCloseTime(branchDTO.getCloseTime());
        exiting.setUpdatedAt(branchDTO.getUpdatedAt());

        Branch updatedBranch = branchRepository.save(exiting);
        return BranchMapper.toDTO(updatedBranch);
    }

    @Override
    public void deleteBranch(Long id) throws Exception {
        Branch exiting = branchRepository.findById(id).orElseThrow(
                ()-> new Exception("Branch not exit...")
        );
        branchRepository.delete(exiting);
    }

    @Override
    public List<BranchDTO> getAllBranchesByStoreId(Long storeId) {
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        return branches.stream().map(BranchMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public BranchDTO getBranchById(Long id) throws Exception {
        Branch exiting = branchRepository.findById(id).orElseThrow(
                ()-> new Exception("Branch not exit...")
        );
        return BranchMapper.toDTO(exiting);
    }
}
