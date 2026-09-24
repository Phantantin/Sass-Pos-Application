package com.tindev.service.impl;

import com.tindev.domain.CatalogStatus;
import com.tindev.domain.InventoryMovementType;
import com.tindev.domain.InventoryTransferStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.modal.Branch;
import com.tindev.modal.Inventory;
import com.tindev.modal.InventoryMovement;
import com.tindev.modal.InventoryTransferRequest;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.InventoryTransferDTO;
import com.tindev.payload.dto.InventoryTransferReviewRequest;
import com.tindev.payload.dto.TransferAvailabilityDTO;
import com.tindev.repository.BranchRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.InventoryTransferRequestRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.service.AuditLogService;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransferServiceImplTest {
    @Mock private InventoryTransferRequestRepository transferRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserService userService;
    @Mock private AuditLogService auditLogService;
    @InjectMocks private InventoryTransferServiceImpl service;

    @Test
    void recommendationsExposeOnlyOtherStoresTransferableQuantity() throws Exception {
        ReflectionTestUtils.setField(service, "minimumReserve", 5);
        Store destinationStore = store(1L, "Cửa hàng A");
        Store sourceStore = store(2L, "Cửa hàng B");
        Branch destination = branch(10L, "A1", destinationStore);
        Branch source = branch(20L, "B1", sourceStore);
        Product product = product(100L);
        User actor = user(1L, UserRole.ROLE_STORE_MANAGER, destinationStore, null);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(branchRepository.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(inventoryRepository.findByProductIdAndQuantityGreaterThanOrderByQuantityDesc(product.getId(), 5))
                .thenReturn(List.of(inventory(1L, source, product, 15), inventory(2L, destination, product, 25)));

        List<TransferAvailabilityDTO> result = service.findAvailability(product.getId(), destination.getId());

        assertEquals(1, result.size());
        assertEquals(source.getId(), result.get(0).getSourceBranchId());
        assertEquals(10, result.get(0).getAvailableQuantity());
    }

    @Test
    void sourceBranchManagerApprovalMovesStockAtomicallyAndWritesBothMovements() throws Exception {
        ReflectionTestUtils.setField(service, "minimumReserve", 5);
        Store sourceStore = store(1L, "Cửa hàng A");
        Store destinationStore = store(2L, "Cửa hàng B");
        Branch sourceBranch = branch(10L, "A1", sourceStore);
        Branch destinationBranch = branch(20L, "B1", destinationStore);
        Product product = product(100L);
        User actor = user(1L, UserRole.ROLE_BRANCH_MANAGER, null, sourceBranch);
        Inventory source = inventory(1L, sourceBranch, product, 20);
        Inventory destination = inventory(2L, destinationBranch, product, 4);
        InventoryTransferRequest request = transfer(50L, product, sourceBranch, destinationBranch,
                user(2L, UserRole.ROLE_STORE_MANAGER, destinationStore, null), 5);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(transferRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(inventoryRepository.findWithLockByProductIdAndBranchId(product.getId(), sourceBranch.getId()))
                .thenReturn(Optional.of(source));
        when(inventoryRepository.findWithLockByProductIdAndBranchId(product.getId(), destinationBranch.getId()))
                .thenReturn(Optional.of(destination));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(request)).thenReturn(request);

        InventoryTransferDTO result = service.approve(request.getId(), new InventoryTransferReviewRequest("Đã kiểm hàng"));

        assertEquals(15, source.getQuantity());
        assertEquals(9, destination.getQuantity());
        assertEquals(InventoryTransferStatus.APPROVED, result.getStatus());
        ArgumentCaptor<InventoryMovement> movements = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository, org.mockito.Mockito.times(2)).save(movements.capture());
        assertEquals(List.of(InventoryMovementType.TRANSFER_OUT, InventoryMovementType.TRANSFER_IN),
                movements.getAllValues().stream().map(InventoryMovement::getType).toList());
    }

    @Test
    void destinationStoreCannotApproveItsOwnIncomingRequest() throws Exception {
        Store sourceStore = store(1L, "Cửa hàng A");
        Store destinationStore = store(2L, "Cửa hàng B");
        Branch source = branch(10L, "A1", sourceStore);
        Branch destination = branch(20L, "B1", destinationStore);
        Product product = product(100L);
        User requester = user(2L, UserRole.ROLE_STORE_ADMIN, destinationStore, null);
        InventoryTransferRequest request = transfer(50L, product, source, destination, requester, 5);
        when(userService.getCurrentUser()).thenReturn(requester);
        when(transferRepository.findById(request.getId())).thenReturn(Optional.of(request));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.approve(request.getId(), new InventoryTransferReviewRequest(null)));

        assertEquals(403, exception.status().value());
        verify(inventoryRepository, never()).findWithLockByProductIdAndBranchId(any(), any());
    }

    private static InventoryTransferRequest transfer(Long id, Product product, Branch source, Branch destination,
                                                     User requester, int quantity) {
        return InventoryTransferRequest.builder()
                .id(id)
                .product(product)
                .sourceBranch(source)
                .destinationBranch(destination)
                .quantity(quantity)
                .requestedBy(requester)
                .status(InventoryTransferStatus.PENDING)
                .reason("Kho nhận sắp hết hàng")
                .build();
    }

    private static Inventory inventory(Long id, Branch branch, Product product, int quantity) {
        return Inventory.builder().id(id).branch(branch).product(product).quantity(quantity).build();
    }

    private static Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Sản phẩm dùng chung");
        product.setSku("GLOBAL-001");
        product.setCatalogStatus(CatalogStatus.APPROVED);
        return product;
    }

    private static Store store(Long id, String brand) {
        Store store = new Store();
        store.setId(id);
        store.setBrand(brand);
        return store;
    }

    private static Branch branch(Long id, String name, Store store) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setName(name);
        branch.setStore(store);
        return branch;
    }

    private static User user(Long id, UserRole role, Store store, Branch branch) {
        User user = new User();
        user.setId(id);
        user.setFullName("Người dùng " + id);
        user.setRole(role);
        user.setStore(store);
        user.setBranch(branch);
        return user;
    }
}
