package com.tindev.repository;

import com.tindev.modal.InventoryMovement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    List<InventoryMovement> findByBranchIdAndProductIdOrderByCreatedAtDesc(Long branchId, Long productId);

    @EntityGraph(attributePaths = {"branch", "product"})
    List<InventoryMovement> findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"branch", "product"})
    List<InventoryMovement> findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long branchId, LocalDateTime from, LocalDateTime to);

    boolean existsByProductId(Long productId);
}
