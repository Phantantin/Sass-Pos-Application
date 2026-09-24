package com.tindev.repository;

import com.tindev.modal.InventoryTransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransferRequestRepository extends JpaRepository<InventoryTransferRequest, Long> {
    List<InventoryTransferRequest> findAllByOrderByCreatedAtDesc();
}
