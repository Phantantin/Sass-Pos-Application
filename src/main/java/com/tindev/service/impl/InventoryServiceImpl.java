package com.tindev.service.impl;

import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.CatalogStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.InventoryMapper;
import com.tindev.mapper.InventoryMovementMapper;
import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.InventoryMovement;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryDTO;
import com.tindev.payload.dto.InventoryMovementDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.service.InventoryService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    @Override
    @Transactional
    public InventoryDTO createInventory(InventoryDTO inventoryDTO) {
        validateQuantity(inventoryDTO.getQuantity());
        validateMinStockLevel(inventoryDTO.getMinStockLevel());
        if (inventoryDTO.getBranchId() == null || inventoryDTO.getProductId() == null) {
            throw ApiException.badRequest("branchId và productId là bắt buộc khi tạo tồn kho");
        }
        Branch branch = requireBranch(inventoryDTO.getBranchId());
        User actor = currentUser();
        assertCanManageBranch(actor, branch);
        Product product = requireProduct(inventoryDTO.getProductId());
        assertProductAvailableToBranch(product, branch);
        if (inventoryRepository.findByProductIdAndBranchId(product.getId(), branch.getId()).isPresent()) {
            throw ApiException.conflict("Sản phẩm đã có bản ghi tồn kho tại chi nhánh này");
        }

        Inventory saved = inventoryRepository.save(InventoryMapper.toEntity(inventoryDTO, branch, product));
        recordMovement(saved, 0, saved.getQuantity(), InventoryMovementType.INITIAL_STOCK,
                defaultReason(inventoryDTO.getReason(), "Tạo tồn kho ban đầu"), actor);
        return InventoryMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO) {
        validateQuantity(inventoryDTO.getQuantity());
        validateMinStockLevel(inventoryDTO.getMinStockLevel());
        Inventory inventory = requireInventory(id);
        User actor = currentUser();
        assertCanManageBranch(actor, inventory.getBranch());

        int before = inventory.getQuantity();
        int after = inventoryDTO.getQuantity();
        int minStockLevel = inventoryDTO.getMinStockLevel() == null
                ? inventory.getMinStockLevel() : inventoryDTO.getMinStockLevel();
        if (before == after && Objects.equals(inventory.getMinStockLevel(), minStockLevel)) {
            return InventoryMapper.toDTO(inventory);
        }
        String reason = defaultReason(inventoryDTO.getReason(), before == after
                ? "Cập nhật ngưỡng tồn tối thiểu" : "Điều chỉnh tồn kho");
        inventory.setQuantity(after);
        inventory.setMinStockLevel(minStockLevel);
        Inventory saved = inventoryRepository.save(inventory);
        if (before != after) {
            recordMovement(saved, before, after, InventoryMovementType.ADJUSTMENT, reason, actor);
        }
        return InventoryMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public void deleteInventory(Long id, String reason) {
        Inventory inventory = requireInventory(id);
        User actor = currentUser();
        assertCanManageBranch(actor, inventory.getBranch());
        if (inventory.getQuantity() != 0) {
            throw ApiException.conflict("Chỉ được xóa bản ghi tồn kho khi số lượng bằng 0");
        }
        recordMovement(inventory, 0, 0, InventoryMovementType.REMOVAL, requiredReason(reason), actor);
        inventoryRepository.delete(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDTO getInventoryById(Long id) {
        Inventory inventory = requireInventory(id);
        assertCanReadBranch(currentUser(), inventory.getBranch());
        return InventoryMapper.toDTO(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryDTO getInventoryByProductIdAndBranchId(Long productId, Long branchId) {
        Branch branch = requireBranch(branchId);
        assertCanReadBranch(currentUser(), branch);
        return inventoryRepository.findByProductIdAndBranchId(productId, branchId).map(InventoryMapper::toDTO)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tồn kho"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryDTO> getAllInventoryByBranchId(Long branchId) {
        Branch branch = requireBranch(branchId);
        assertCanReadBranch(currentUser(), branch);
        return inventoryRepository.findByBranchId(branchId).stream().map(InventoryMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementDTO> getMovementHistory(Long branchId, Long productId) {
        Branch branch = requireBranch(branchId);
        assertCanReadBranch(currentUser(), branch);
        if (productId != null) {
            Product product = requireProduct(productId);
            assertProductAvailableToBranch(product, branch);
            return inventoryMovementRepository.findByBranchIdAndProductIdOrderByCreatedAtDesc(branchId, productId)
                    .stream().map(InventoryMovementMapper::toDTO).toList();
        }
        return inventoryMovementRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                .stream().map(InventoryMovementMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public InventoryDTO adjustInventoryForTransaction(Long productId, Long branchId, int delta,
                                                       InventoryMovementType type, String reason, User performedBy) {
        if (type == null || performedBy == null) {
            throw ApiException.badRequest("Loại biến động và người thực hiện là bắt buộc");
        }
        Inventory inventory = inventoryRepository.findWithLockByProductIdAndBranchId(productId, branchId)
                .orElseThrow(() -> ApiException.conflict("Không tìm thấy tồn kho tại chi nhánh"));
        int before = inventory.getQuantity();
        final int after;
        try {
            after = Math.addExact(before, delta);
        } catch (ArithmeticException exception) {
            throw ApiException.badRequest("Số lượng tồn kho vượt quá giới hạn cho phép");
        }
        if (after < 0) {
            throw ApiException.conflict("Tồn kho không đủ cho thao tác này");
        }
        if (before == after) {
            return InventoryMapper.toDTO(inventory);
        }
        inventory.setQuantity(after);
        Inventory saved = inventoryRepository.save(inventory);
        recordMovement(saved, before, after, type, defaultReason(reason, defaultReasonFor(type)), performedBy);
        return InventoryMapper.toDTO(saved);
    }

    private Inventory requireInventory(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tồn kho"));
    }

    private Branch requireBranch(Long id) {
        if (id == null) {
            throw ApiException.badRequest("branchId là bắt buộc");
        }
        return branchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
    }

    private Product requireProduct(Long id) {
        if (id == null) {
            throw ApiException.badRequest("productId là bắt buộc");
        }
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw ApiException.forbidden("Không xác định được tài khoản hiện tại");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw ApiException.badRequest("Số lượng tồn kho phải lớn hơn hoặc bằng 0");
        }
    }

    private void validateMinStockLevel(Integer minStockLevel) {
        if (minStockLevel != null && minStockLevel < 0) {
            throw ApiException.badRequest("Ngưỡng tồn tối thiểu phải lớn hơn hoặc bằng 0");
        }
    }

    private void assertProductAvailableToBranch(Product product, Branch branch) {
        boolean belongsToStore = product.getStore() != null && branch.getStore() != null
                && Objects.equals(product.getStore().getId(), branch.getStore().getId());
        if (!belongsToStore && product.getCatalogStatus() != CatalogStatus.APPROVED) {
            throw ApiException.badRequest("Sản phẩm chưa được HQ duyệt cho danh mục dùng chung");
        }
    }

    private void assertCanManageBranch(User user, Branch branch) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        Store store = branch.getStore();
        boolean isOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(user, store);
        boolean isStoreManager = user.getRole() == UserRole.ROLE_STORE_MANAGER && belongsToStore(user, store);
        boolean isAssignedBranchManager = user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && user.getBranch() != null && Objects.equals(user.getBranch().getId(), branch.getId());
        if (!isOwner && !isStoreManager && !isAssignedBranchManager) {
            throw ApiException.forbidden("Bạn không có quyền quản lý tồn kho của chi nhánh này");
        }
    }

    private void assertCanReadBranch(User user, Branch branch) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        Store store = branch.getStore();
        boolean isOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(user, store);
        boolean isStoreManager = user.getRole() == UserRole.ROLE_STORE_MANAGER && belongsToStore(user, store);
        boolean isAssignedBranchEmployee = (user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                || user.getRole() == UserRole.ROLE_BRANCH_CASHIER)
                && user.getBranch() != null && Objects.equals(user.getBranch().getId(), branch.getId());
        if (!isOwner && !isStoreManager && !isAssignedBranchEmployee) {
            throw ApiException.forbidden("Bạn không có quyền xem tồn kho của chi nhánh này");
        }
    }

    private boolean belongsToStore(User user, Store store) {
        if (store == null) return false;
        if (user.getStore() != null && Objects.equals(user.getStore().getId(), store.getId())) return true;
        return store.getStoreAdmin() != null && Objects.equals(store.getStoreAdmin().getId(), user.getId());
    }

    private void recordMovement(Inventory inventory, int before, int after, InventoryMovementType type,
                                String reason, User performedBy) {
        inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .performedBy(performedBy)
                .type(type)
                .quantityBefore(before)
                .quantityAfter(after)
                .quantityDelta(after - before)
                .reason(reason)
                .build());
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw ApiException.badRequest("Lý do điều chỉnh tồn kho là bắt buộc");
        }
        return reason.trim();
    }

    private String defaultReason(String reason, String fallback) {
        return reason == null || reason.trim().isEmpty() ? fallback : reason.trim();
    }

    private String defaultReasonFor(InventoryMovementType type) {
        return switch (type) {
            case INITIAL_STOCK -> "Tạo tồn kho ban đầu";
            case ADJUSTMENT -> "Điều chỉnh tồn kho";
            case SALE -> "Bán hàng";
            case REFUND -> "Hoàn tiền";
            case REMOVAL -> "Xóa bản ghi tồn kho";
            case TRANSFER_OUT -> "Xuất kho điều chuyển";
            case TRANSFER_IN -> "Nhập kho điều chuyển";
        };
    }
}
