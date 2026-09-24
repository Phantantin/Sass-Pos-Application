package com.tindev.mapper;

import com.tindev.modal.AuditLog;
import com.tindev.payload.dto.AuditLogDTO;

public final class AuditLogMapper {
    private AuditLogMapper() {
    }

    public static AuditLogDTO toDTO(AuditLog auditLog) {
        return AuditLogDTO.builder()
                .id(auditLog.getId())
                .storeId(auditLog.getStore() == null ? null : auditLog.getStore().getId())
                .storeBrand(auditLog.getStore() == null ? null : auditLog.getStore().getBrand())
                .actorId(auditLog.getActor() == null ? null : auditLog.getActor().getId())
                .actorName(auditLog.getActor() == null ? null : auditLog.getActor().getFullName())
                .action(auditLog.getAction())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .detail(auditLog.getDetail())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
