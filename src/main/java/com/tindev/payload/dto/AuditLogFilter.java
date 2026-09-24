package com.tindev.payload.dto;

import java.time.LocalDateTime;

/**
 * Server-side filters accepted by the audit-log read APIs. Values are
 * normalized and validated by {@code AuditLogService}; callers must not use
 * this type to bypass the authorization scope enforced by that service.
 */
public record AuditLogFilter(
        int page,
        int pageSize,
        String action,
        String entityType,
        LocalDateTime from,
        LocalDateTime to) {
}
