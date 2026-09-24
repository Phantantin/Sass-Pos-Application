package com.tindev.service.impl;

import com.tindev.domain.CatalogStatus;
import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.InventoryTransferStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.InventoryMovement;
import com.tindev.modal.InventoryTransferRequest;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryTransferCreateRequest;
import com.tindev.payload.dto.InventoryTransferDTO;
import com.tindev.payload.dto.InventoryTransferReviewRequest;
import com.tindev.payload.dto.TransferAvailabilityDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.InventoryTransferRequestRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.service.AuditLogService;
import com.tindev.service.InventoryTransferService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InventoryTransferServiceImpl implements InventoryTransferService {
    private final InventoryTransferRequestRepository transferRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @Value("${app.inventory.transfer-min-reserve:5}")
    private int minimumReserve;

    @Override
    @Transactional(readOnly = true)
    public List<TransferAvailabilityDTO> findAvailability(Long productId, Long destinationBranchId) {
        Product product = requireProduct(productId);
        if (product.getCatalogStatus() != CatalogStatus.APPROVED) {
            throw ApiException.conflict("Sản phẩm chưa được HQ duyệt cho danh mục dùng chung");
        }
        Branch destination = requireBranch(destinationBranchId);
        requireDestinationAccess(currentUser(), destination);
        return inventoryRepository.findByProductIdAndQuantityGreaterThanOrderByQuantityDesc(productId, minimumReserve)
                .stream()
                .filter(inventory -> !sameStore(inventory.getBranch().getStore(), destination.getStore()))
                .map(inventory -> availability(inventory, product))
                .toList();
    }

    @Override
    @Transactional
    public InventoryTransferDTO create(InventoryTransferCreateRequest request) {
        User actor = currentUser();
        Product product = requireProduct(request.productId());
        if (product.getCatalogStatus() != CatalogStatus.APPROVED) {
            throw ApiException.conflict("Chỉ sản phẩm đã được HQ duyệt mới có thể điều chuyển liên cửa hàng");
        }
        Branch source = requireBranch(request.sourceBranchId());
        Branch destination = requireBranch(request.destinationBranchId());
        if (sameStore(source.getStore(), destination.getStore())) {
            throw ApiException.badRequest("Điều chuyển nội bộ yêu cầu hai cửa hàng khác nhau");
        }
        requireDestinationAccess(actor, destination);
        Inventory sourceInventory = inventoryRepository.findByProductIdAndBranchId(product.getId(), source.getId())
                .orElseThrow(() -> ApiException.notFound("Kho nguồn không có sản phẩm này"));
        if (transferable(sourceInventory) < request.quantity()) {
            throw ApiException.conflict("Số lượng yêu cầu vượt quá tồn kho có thể điều chuyển");
        }
        InventoryTransferRequest saved = transferRepository.save(InventoryTransferRequest.builder()
                .product(product)
                .sourceBranch(source)
                .destinationBranch(destination)
                .quantity(request.quantity())
                .reason(request.reason().trim())
                .requestedBy(actor)
                .status(InventoryTransferStatus.PENDING)
                .build());
        auditLogService.record(destination.getStore(), actor, "TRANSFER_REQUESTED", "INVENTORY_TRANSFER",
                saved.getId(), transferDetail(saved));
        return toDTO(saved, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryTransferDTO> getVisibleRequests() {
        User actor = currentUser();
        return transferRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(request -> canView(actor, request))
                .map(request -> toDTO(request, actor))
                .toList();
    }

    @Override
    @Transactional
    public InventoryTransferDTO approve(Long id, InventoryTransferReviewRequest review) {
        InventoryTransferRequest request = requireTransfer(id);
        User actor = currentUser();
        requireSourceApproval(actor, request.getSourceBranch());
        requirePending(request);

        Inventory source = inventoryRepository.findWithLockByProductIdAndBranchId(
                        request.getProduct().getId(), request.getSourceBranch().getId())
                .orElseThrow(() -> ApiException.conflict("Kho nguồn không còn sản phẩm cần điều chuyển"));
        if (transferable(source) < request.getQuantity()) {
            throw ApiException.conflict("Kho nguồn không còn đủ số lượng có thể điều chuyển");
        }
        Inventory destination = inventoryRepository.findWithLockByProductIdAndBranchId(
                        request.getProduct().getId(), request.getDestinationBranch().getId())
                .orElseGet(() -> Inventory.builder()
                        .branch(request.getDestinationBranch())
                        .product(request.getProduct())
                        .quantity(0)
                        .build());

        int sourceBefore = source.getQuantity();
        int destinationBefore = destination.getQuantity();
        source.setQuantity(sourceBefore - request.getQuantity());
        destination.setQuantity(Math.addExact(destinationBefore, request.getQuantity()));
        inventoryRepository.save(source);
        inventoryRepository.save(destination);
        recordMovement(source, sourceBefore, source.getQuantity(), InventoryMovementType.TRANSFER_OUT,
                "Điều chuyển #" + request.getId() + " đến " + request.getDestinationBranch().getName(), actor);
        recordMovement(destination, destinationBefore, destination.getQuantity(), InventoryMovementType.TRANSFER_IN,
                "Điều chuyển #" + request.getId() + " từ " + request.getSourceBranch().getName(), actor);

        review(request, actor, InventoryTransferStatus.APPROVED, review == null ? null : review.note());
        InventoryTransferRequest saved = transferRepository.save(request);
        auditLogService.record(request.getSourceBranch().getStore(), actor, "TRANSFER_APPROVED",
                "INVENTORY_TRANSFER", saved.getId(), transferDetail(saved));
        auditLogService.record(request.getDestinationBranch().getStore(), actor, "TRANSFER_RECEIVED",
                "INVENTORY_TRANSFER", saved.getId(), transferDetail(saved));
        return toDTO(saved, actor);
    }

    @Override
    @Transactional
    public InventoryTransferDTO reject(Long id, InventoryTransferReviewRequest review) {
        InventoryTransferRequest request = requireTransfer(id);
        User actor = currentUser();
        requireSourceApproval(actor, request.getSourceBranch());
        requirePending(request);
        review(request, actor, InventoryTransferStatus.REJECTED, review == null ? null : review.note());
        InventoryTransferRequest saved = transferRepository.save(request);
        auditLogService.record(request.getSourceBranch().getStore(), actor, "TRANSFER_REJECTED",
                "INVENTORY_TRANSFER", saved.getId(), transferDetail(saved));
        return toDTO(saved, actor);
    }

    @Override
    @Transactional
    public InventoryTransferDTO cancel(Long id) {
        InventoryTransferRequest request = requireTransfer(id);
        User actor = currentUser();
        requirePending(request);
        if (actor.getRole() != UserRole.ROLE_ADMIN
                && !Objects.equals(request.getRequestedBy().getId(), actor.getId())) {
            throw ApiException.forbidden("Chỉ người yêu cầu hoặc HQ có thể hủy yêu cầu");
        }
        review(request, actor, InventoryTransferStatus.CANCELLED, "Người yêu cầu đã hủy");
        return toDTO(transferRepository.save(request), actor);
    }

    private TransferAvailabilityDTO availability(Inventory inventory, Product product) {
        Store store = inventory.getBranch().getStore();
        return TransferAvailabilityDTO.builder()
                .productId(product.getId())
                .productName(product.getName())
                .sku(product.getSku())
                .image(product.getImage())
                .sourceStoreId(store.getId())
                .sourceStoreName(store.getBrand())
                .sourceBranchId(inventory.getBranch().getId())
                .sourceBranchName(inventory.getBranch().getName())
                .availableQuantity(transferable(inventory))
                .build();
    }

    private InventoryTransferDTO toDTO(InventoryTransferRequest request, User actor) {
        boolean canReview = request.getStatus() == InventoryTransferStatus.PENDING
                && canApproveSource(actor, request.getSourceBranch());
        return InventoryTransferDTO.builder()
                .id(request.getId())
                .productId(request.getProduct().getId())
                .productName(request.getProduct().getName())
                .sku(request.getProduct().getSku())
                .sourceStoreId(request.getSourceBranch().getStore().getId())
                .sourceStoreName(request.getSourceBranch().getStore().getBrand())
                .sourceBranchId(request.getSourceBranch().getId())
                .sourceBranchName(request.getSourceBranch().getName())
                .destinationStoreId(request.getDestinationBranch().getStore().getId())
                .destinationStoreName(request.getDestinationBranch().getStore().getBrand())
                .destinationBranchId(request.getDestinationBranch().getId())
                .destinationBranchName(request.getDestinationBranch().getName())
                .quantity(request.getQuantity())
                .status(request.getStatus())
                .reason(request.getReason())
                .reviewNote(request.getReviewNote())
                .requestedById(request.getRequestedBy().getId())
                .requestedByName(request.getRequestedBy().getFullName())
                .reviewedById(request.getReviewedBy() == null ? null : request.getReviewedBy().getId())
                .reviewedByName(request.getReviewedBy() == null ? null : request.getReviewedBy().getFullName())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .updatedAt(request.getUpdatedAt())
                .canApprove(canReview)
                .canReject(canReview)
                .canCancel(request.getStatus() == InventoryTransferStatus.PENDING
                        && (actor.getRole() == UserRole.ROLE_ADMIN
                        || Objects.equals(actor.getId(), request.getRequestedBy().getId())))
                .build();
    }

    private void requireDestinationAccess(User actor, Branch destination) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) return;
        if ((actor.getRole() == UserRole.ROLE_STORE_ADMIN || actor.getRole() == UserRole.ROLE_STORE_MANAGER)
                && belongsToStore(actor, destination.getStore())) return;
        if (actor.getRole() == UserRole.ROLE_BRANCH_MANAGER && actor.getBranch() != null
                && Objects.equals(actor.getBranch().getId(), destination.getId())) return;
        throw ApiException.forbidden("Bạn chỉ có thể yêu cầu nhập về cửa hàng hoặc chi nhánh được phân công");
    }

    private void requireSourceApproval(User actor, Branch source) {
        if (!canApproveSource(actor, source)) {
            throw ApiException.forbidden("Bạn không có quyền phê duyệt xuất kho của chi nhánh nguồn");
        }
    }

    private boolean canApproveSource(User actor, Branch source) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) return true;
        if ((actor.getRole() == UserRole.ROLE_STORE_ADMIN || actor.getRole() == UserRole.ROLE_STORE_MANAGER)
                && belongsToStore(actor, source.getStore())) return true;
        return actor.getRole() == UserRole.ROLE_BRANCH_MANAGER && actor.getBranch() != null
                && Objects.equals(actor.getBranch().getId(), source.getId());
    }

    private boolean canView(User actor, InventoryTransferRequest request) {
        if (actor.getRole() == UserRole.ROLE_ADMIN) return true;
        if (actor.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            return actor.getBranch() != null && (Objects.equals(actor.getBranch().getId(), request.getSourceBranch().getId())
                    || Objects.equals(actor.getBranch().getId(), request.getDestinationBranch().getId()));
        }
        return belongsToStore(actor, request.getSourceBranch().getStore())
                || belongsToStore(actor, request.getDestinationBranch().getStore());
    }

    private void recordMovement(Inventory inventory, int before, int after, InventoryMovementType type,
                                String reason, User actor) {
        movementRepository.save(InventoryMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .performedBy(actor)
                .type(type)
                .quantityBefore(before)
                .quantityAfter(after)
                .quantityDelta(after - before)
                .reason(reason)
                .build());
    }

    private void review(InventoryTransferRequest request, User actor, InventoryTransferStatus status, String note) {
        request.setStatus(status);
        request.setReviewedBy(actor);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note == null || note.isBlank() ? null : note.trim());
    }

    private int transferable(Inventory inventory) {
        return Math.max(0, inventory.getQuantity() - Math.max(0, minimumReserve));
    }

    private void requirePending(InventoryTransferRequest request) {
        if (request.getStatus() != InventoryTransferStatus.PENDING) {
            throw ApiException.conflict("Yêu cầu điều chuyển đã được xử lý");
        }
    }

    private InventoryTransferRequest requireTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu điều chuyển"));
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
    }

    private Branch requireBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi nhánh"));
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw ApiException.forbidden("Không xác định được tài khoản hiện tại");
        }
    }

    private Store storeFor(User user) {
        if (user.getStore() != null) return user.getStore();
        if (user.getBranch() != null) return user.getBranch().getStore();
        throw ApiException.forbidden("Tài khoản chưa thuộc cửa hàng hợp lệ");
    }

    private boolean belongsToStore(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_STORE_ADMIN && store != null
                && store.getStoreAdmin() != null
                && Objects.equals(store.getStoreAdmin().getId(), user.getId())) return true;
        try {
            return sameStore(storeFor(user), store);
        } catch (ApiException exception) {
            return false;
        }
    }

    private boolean sameStore(Store left, Store right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private String transferDetail(InventoryTransferRequest request) {
        return "Product=" + request.getProduct().getId() + ", quantity=" + request.getQuantity()
                + ", sourceBranch=" + request.getSourceBranch().getId()
                + ", destinationBranch=" + request.getDestinationBranch().getId();
    }
}
