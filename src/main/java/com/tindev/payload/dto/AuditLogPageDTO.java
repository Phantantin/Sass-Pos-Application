package com.tindev.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A deterministic, server-paginated audit log response. Results are ordered
 * by {@code createdAt DESC, id DESC} so records created at the same timestamp
 * do not move unpredictably between pages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogPageDTO {
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private List<AuditLogDTO> logs;
}
