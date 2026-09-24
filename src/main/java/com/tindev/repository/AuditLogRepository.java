package com.tindev.repository;

import com.tindev.modal.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @EntityGraph(attributePaths = {"store", "actor"})
    @Query("""
            SELECT auditLog
            FROM AuditLog auditLog
            WHERE (:storeId IS NULL OR auditLog.store.id = :storeId)
              AND (:action IS NULL OR LOWER(auditLog.action) = LOWER(:action))
              AND (:entityType IS NULL OR LOWER(auditLog.entityType) = LOWER(:entityType))
              AND (:fromTime IS NULL OR auditLog.createdAt >= :fromTime)
              AND (:toTime IS NULL OR auditLog.createdAt <= :toTime)
            """)
    Page<AuditLog> search(
            @Param("storeId") Long storeId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("fromTime") LocalDateTime from,
            @Param("toTime") LocalDateTime to,
            Pageable pageable);
}
