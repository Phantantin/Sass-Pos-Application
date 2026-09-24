package com.tindev.service;

import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.AuditLogDTO;
import com.tindev.payload.dto.AuditLogFilter;
import com.tindev.payload.dto.AuditLogPageDTO;

public interface AuditLogService {
    void record(Store store, User actor, String action, String entityType, Long entityId, String detail);
    AuditLogPageDTO getStoreAuditLogs(Long storeId, AuditLogFilter filter);
    AuditLogPageDTO getAllAuditLogs(Long storeId, AuditLogFilter filter);
}
