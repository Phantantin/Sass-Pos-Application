package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.BranchMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.BranchDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.BranchService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public BranchDTO createBranch(BranchDTO branchDTO) {
        User currentUser = currentUser();
        Store store = resolveStoreForCreate(currentUser, branchDTO.getStoreId());
        requireStoreOwnerOrAdmin(currentUser, store);
        validateBranchDetails(branchDTO);
        subscriptionService.assertCanCreateBranch(store);

        Branch branch = new Branch();
        branch.setStore(store);
        applyDetails(branch, branchDTO);
        branch.setCreatedAt(LocalDateTime.now());
        branch.setUpdatedAt(LocalDateTime.now());
        return BranchMapper.toDTO(branchRepository.save(branch));
    }

    @Override
    @Transactional
    public BranchDTO updateBranch(Long id, BranchDTO branchDTO) {
        Branch branch = findBranch(id);
        User currentUser = currentUser();
        requireBranchUpdatePermission(currentUser, branch);
        validateBranchDetails(branchDTO);

        applyDetails(branch, branchDTO);
        branch.setUpdatedAt(LocalDateTime.now());
        return BranchMapper.toDTO(branchRepository.save(branch));
    }

    @Override
    @Transactional
    public void deleteBranch(Long id) {
        Branch branch = findBranch(id);
        User currentUser = currentUser();
        requireStoreOwnerOrAdmin(currentUser, branch.getStore());
        if (!userRepository.findByBranchId(id).isEmpty()) {
            throw ApiException.conflict("Không thể xóa chi nhánh vẫn còn nhân viên được phân công");
        }
        branchRepository.delete(branch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDTO> getAllBranchesByStoreId(Long storeId) {
        Store store = findStore(storeId);
        User currentUser = currentUser();
        requireStoreAccess(currentUser, store);

        if (isBranchScoped(currentUser)) {
            Branch assignedBranch = currentUser.getBranch();
            if (assignedBranch == null || !sameStore(assignedBranch.getStore(), store)) {
                throw ApiException.forbidden("Tài khoản chưa được phân công chi nhánh hợp lệ");
            }
            return List.of(BranchMapper.toDTO(assignedBranch));
        }

        return branchRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream().map(BranchMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchDTO getBranchById(Long id) {
        Branch branch = findBranch(id);
        User currentUser = currentUser();
        requireBranchReadPermission(currentUser, branch);
        return BranchMapper.toDTO(branch);
    }

    private Branch findBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
    }

    private Store findStore(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private Store resolveStoreForCreate(User currentUser, Long requestedStoreId) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            if (requestedStoreId == null) {
                throw ApiException.badRequest("Quản trị viên phải chỉ định storeId khi tạo chi nhánh");
            }
            return findStore(requestedStoreId);
        }

        if (currentUser.getRole() != UserRole.ROLE_STORE_ADMIN) {
            throw ApiException.forbidden("Chỉ chủ cửa hàng hoặc quản lý cửa hàng mới có thể tạo chi nhánh");
        }
        if (requestedStoreId != null) {
            Store requestedStore = findStore(requestedStoreId);
            if (!belongsToStore(currentUser, requestedStore)) {
                throw ApiException.forbidden("Không thể tạo chi nhánh cho cửa hàng khác");
            }
            return requestedStore;
        }
        Store ownedStore = currentUser.getStore() != null
                ? currentUser.getStore() : storeRepository.findByStoreAdminId(currentUser.getId());
        if (ownedStore == null) throw ApiException.forbidden("Tài khoản chưa quản lý cửa hàng hợp lệ");
        return ownedStore;
    }

    private void requireStoreOwnerOrAdmin(User currentUser, Store store) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(currentUser, store)) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền quản lý chi nhánh của cửa hàng này");
    }

    private void requireStoreAccess(User currentUser, Store store) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(currentUser, store)) {
            return;
        }
        Store currentStore = storeFor(currentUser);
        if (!sameStore(currentStore, store)) {
            throw ApiException.forbidden("Dữ liệu chi nhánh không thuộc cửa hàng hiện tại");
        }
    }

    private void requireBranchReadPermission(User currentUser, Branch branch) {
        requireStoreAccess(currentUser, branch.getStore());
        if (isBranchScoped(currentUser)
                && (currentUser.getBranch() == null
                || !Objects.equals(currentUser.getBranch().getId(), branch.getId()))) {
            throw ApiException.forbidden("Bạn chỉ có thể xem chi nhánh được phân công");
        }
    }

    private void requireBranchUpdatePermission(User currentUser, Branch branch) {
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                && belongsToStore(currentUser, branch.getStore())) {
            return;
        }
        if (currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && currentUser.getBranch() != null
                && Objects.equals(currentUser.getBranch().getId(), branch.getId())) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền cập nhật chi nhánh này");
    }

    private Store storeFor(User user) {
        if (user.getStore() != null) {
            return user.getStore();
        }
        if (user.getBranch() != null && user.getBranch().getStore() != null) {
            return user.getBranch().getStore();
        }
        Store ownedStore = storeRepository.findByStoreAdminId(user.getId());
        if (ownedStore != null) {
            return ownedStore;
        }
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private boolean isBranchScoped(User user) {
        return user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                || user.getRole() == UserRole.ROLE_BRANCH_CASHIER;
    }

    private boolean sameStore(Store left, Store right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private boolean belongsToStore(User user, Store store) {
        if (user.getStore() != null && sameStore(user.getStore(), store)) return true;
        return store != null && store.getStoreAdmin() != null
                && Objects.equals(store.getStoreAdmin().getId(), user.getId());
    }

    private void validateBranchDetails(BranchDTO branchDTO) {
        if (branchDTO.getOpenTime() == null || branchDTO.getCloseTime() == null
                || !branchDTO.getOpenTime().isBefore(branchDTO.getCloseTime())) {
            throw ApiException.badRequest("Giờ mở cửa phải trước giờ đóng cửa");
        }
        if (branchDTO.getWorkingDays() == null || branchDTO.getWorkingDays().isEmpty()
                || branchDTO.getWorkingDays().stream().anyMatch(day -> day == null || day.isBlank())) {
            throw ApiException.badRequest("Cần chọn ít nhất một ngày làm việc hợp lệ");
        }
    }

    private void applyDetails(Branch branch, BranchDTO branchDTO) {
        branch.setName(requiredText(branchDTO.getName(), "Tên chi nhánh"));
        branch.setAddress(requiredText(branchDTO.getAddress(), "Địa chỉ chi nhánh"));
        branch.setPhone(requiredText(branchDTO.getPhone(), "Số điện thoại chi nhánh"));
        branch.setEmail(trimToNull(branchDTO.getEmail()));
        branch.setWorkingDays(branchDTO.getWorkingDays().stream().map(String::trim).toList());
        branch.setOpenTime(branchDTO.getOpenTime());
        branch.setCloseTime(branchDTO.getCloseTime());
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " là bắt buộc");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }
}
