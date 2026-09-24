package com.tindev.repository;

import com.tindev.modal.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {
    List<RefundItem> findByRefundOrderId(Long orderId);
}
