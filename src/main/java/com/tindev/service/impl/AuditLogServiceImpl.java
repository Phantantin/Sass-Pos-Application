package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.AuditLogMapper;
import com.tindev.modal.AuditLog;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.AuditLogFilter;
import com.tindev.payload.dto.AuditLogPageDTO;
import com.tindev.repository.AuditLogRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.AuditLogService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FILTER_LENGTH = 80;

    private final AuditLogRepository auditLogRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    @Transactional
    public void record(Store store, User actor, String action, String entityType, Long entityId, String detail) {
        if (action == null || action.isBlank() || entityType == null || entityType.isBlank()) {
            throw ApiException.badRequest("Audit action và entity type là bắt buộc");
        }
        auditLogRepository.save(AuditLog.builder()
                .store(store)
                .actor(actor)
                .action(action.trim())
                .entityType(entityType.trim())
                .entityId(entityId)
                .detail(detail == null || detail.isBlank() ? null : detail.trim())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogPageDTO getStoreAuditLogs(Long storeId, AuditLogFilter filter) {
        User user = currentUser();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
        requireStoreAuditRead(user, store);
        return findAuditLogs(store.getId(), filter);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogPageDTO getAllAuditLogs(Long storeId, AuditLogFilter filter) {
        if (currentUser().getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.forbidden("Chỉ quản trị viên hệ thống được xem toàn bộ audit log");
        }
        if (storeId != null && !storeRepository.existsById(storeId)) {
            throw ApiException.notFound("Không tìm thấy cửa hàng");
        }
        return findAuditLogs(storeId, filter);
    }

    private AuditLogPageDTO findAuditLogs(Long storeId, AuditLogFilter requestedFilter) {
        AuditLogFilter filter = normalizeAndValidate(requestedFilter);
        Pageable pageable = PageRequest.of(filter.page(), filter.pageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AuditLog> auditLogs = auditLogRepository.search(
                storeId,
                filter.action(),
                filter.entityType(),
                filter.from(),
                filter.to(),
                pageable);

        return AuditLogPageDTO.builder()
                .page(auditLogs.getNumber())
                .pageSize(auditLogs.getSize())
                .totalElements(auditLogs.getTotalElements())
                .totalPages(auditLogs.getTotalPages())
                .logs(auditLogs.getContent().stream().map(AuditLogMapper::toDTO).toList())
                .build();
    }

    private AuditLogFilter normalizeAndValidate(AuditLogFilter filter) {
        if (filter == null) {
            throw ApiException.badRequest("Bộ lọc audit log là bắt buộc");
        }
        if (filter.page() < 0) {
            throw ApiException.badRequest("Số trang không được âm");
        }
        if (filter.pageSize() < 1 || filter.pageSize() > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("Kích thước trang phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiException.badRequest("Thời gian bắt đầu không được sau thời gian kết thúc");
        }
        return new AuditLogFilter(
                filter.page(),
                filter.pageSize(),
                normalizeFilter(filter.action(), "Hành động"),
                normalizeFilter(filter.entityType(), "Loại đối tượng"),
                filter.from(),
                filter.to());
    }

    private String normalizeFilter(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_FILTER_LENGTH) {
            throw ApiException.badRequest(fieldName + " không được dài quá " + MAX_FILTER_LENGTH + " ký tự");
        }
        return normalized;
    }

    private void requireStoreAuditRead(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean ownsStore = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && store.getStoreAdmin() != null && Objects.equals(store.getStoreAdmin().getId(), user.getId());
        boolean worksForStore = user.getStore() != null && Objects.equals(user.getStore().getId(), store.getId());
        if (!ownsStore && !worksForStore) {
            throw ApiException.forbidden("Không có quyền xem audit log của cửa hàng này");
        }
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại");
        }
    }
}
