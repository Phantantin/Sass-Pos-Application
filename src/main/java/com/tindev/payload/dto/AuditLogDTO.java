package com.tindev.payload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
    private Long id;
    private Long storeId;
    private String storeBrand;
    private Long actorId;
    private String actorName;
    private String action;
    private String entityType;
    private Long entityId;
    private String detail;
    private LocalDateTime createdAt;
}
