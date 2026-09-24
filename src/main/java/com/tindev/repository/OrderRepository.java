package com.tindev.repository;

import com.tindev.modal.Order;
import com.tindev.modal.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
    @EntityGraph(attributePaths = {"branch"})
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
    @EntityGraph(attributePaths = {"branch"})
    Page<Order> findByCustomerIdAndBranchIdOrderByCreatedAtDesc(Long customerId, Long branchId, Pageable pageable);
    List<Order> findByBranchId(Long branchId);
    List<Order> findByCashierId(Long cashierId);
    List<Order> findByBranchIdAndCreatedAtBetween(
            Long branchId, LocalDateTime from, LocalDateTime to);

    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findDistinctByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findDistinctByBranchIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long branchId, LocalDateTime from, LocalDateTime to);

    List<Order> findByCashierAndCreatedAtBetween(
            User cashier, LocalDateTime from, LocalDateTime to
    );

    List<Order> findTo5ByBranchIdOrderByCreatedAtDesc(Long branchId);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
