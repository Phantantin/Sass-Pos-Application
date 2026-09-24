package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.modal.AuditLog;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.AuditLogFilter;
import com.tindev.payload.dto.AuditLogPageDTO;
import com.tindev.repository.AuditLogRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserService userService;
    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Test
    void recordNormalizesValuesBeforePersisting() {
        Store store = store(10L, "Cửa hàng A");
        User actor = user(1L, UserRole.ROLE_STORE_ADMIN);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.record(store, actor, " ORDER_CREATED ", " Order ", 25L, "   ");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog persisted = captor.getValue();
        assertEquals(store, persisted.getStore());
        assertEquals(actor, persisted.getActor());
        assertEquals("ORDER_CREATED", persisted.getAction());
        assertEquals("Order", persisted.getEntityType());
        assertEquals(25L, persisted.getEntityId());
        assertNull(persisted.getDetail());
    }

    @Test
    void recordRejectsMissingRequiredAuditMetadata() {
        ApiException exception = assertThrows(ApiException.class,
                () -> auditLogService.record(store(10L, "Cửa hàng A"), user(1L, UserRole.ROLE_STORE_ADMIN), " ", "Order", 25L, null));

        assertEquals(400, exception.status().value());
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void storeEmployeeCanReadOnlyTheirStoreAuditLogsWithServerSideFiltersAndPagination() throws Exception {
        Store store = store(10L, "Cửa hàng A");
        User employee = user(2L, UserRole.ROLE_BRANCH_CASHIER);
        employee.setStore(store);
        AuditLog log = auditLog(store, employee, "ORDER_CREATED");
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 1, 23, 59, 59);
        AuditLogFilter filter = new AuditLogFilter(0, 20, " ORDER_CREATED ", " Order ", from, to);
        when(storeRepository.findById(store.getId())).thenReturn(Optional.of(store));
        when(userService.getCurrentUser()).thenReturn(employee);
        when(auditLogRepository.search(
                eq(store.getId()), eq("ORDER_CREATED"), eq("Order"), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 47));

        AuditLogPageDTO result = auditLogService.getStoreAuditLogs(store.getId(), filter);

        assertEquals(0, result.getPage());
        assertEquals(20, result.getPageSize());
        assertEquals(47, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getLogs().size());
        assertEquals(store.getId(), result.getLogs().get(0).getStoreId());
        assertEquals(employee.getId(), result.getLogs().get(0).getActorId());
        assertEquals("ORDER_CREATED", result.getLogs().get(0).getAction());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).search(
                eq(store.getId()), eq("ORDER_CREATED"), eq("Order"), eq(from), eq(to), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }

    @Test
    void userFromAnotherStoreCannotReadAuditLogs() throws Exception {
        Store requestedStore = store(10L, "Cửa hàng A");
        Store otherStore = store(20L, "Cửa hàng B");
        User otherStoreManager = user(2L, UserRole.ROLE_STORE_MANAGER);
        otherStoreManager.setStore(otherStore);
        when(storeRepository.findById(requestedStore.getId())).thenReturn(Optional.of(requestedStore));
        when(userService.getCurrentUser()).thenReturn(otherStoreManager);

        ApiException exception = assertThrows(ApiException.class,
                () -> auditLogService.getStoreAuditLogs(requestedStore.getId(), filter()));

        assertEquals(403, exception.status().value());
        verify(auditLogRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void systemAdminCanNarrowGlobalAuditFeedToOneStore() throws Exception {
        Store store = store(10L, "Cửa hàng A");
        User admin = user(1L, UserRole.ROLE_ADMIN);
        AuditLog log = auditLog(store, admin, "EMPLOYEE_CREATED");
        when(userService.getCurrentUser()).thenReturn(admin);
        when(storeRepository.existsById(store.getId())).thenReturn(true);
        when(auditLogRepository.search(
                eq(store.getId()), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log, log, log, log), PageRequest.of(1, 10), 14));

        AuditLogPageDTO result = auditLogService.getAllAuditLogs(store.getId(), new AuditLogFilter(1, 10, null, null, null, null));

        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());
        assertEquals(14, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals("EMPLOYEE_CREATED", result.getLogs().get(0).getAction());
        verify(storeRepository).existsById(store.getId());
        verify(auditLogRepository).search(eq(store.getId()), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void nonSystemAdminCannotReadCrossTenantAuditLogFeed() throws Exception {
        when(userService.getCurrentUser()).thenReturn(user(2L, UserRole.ROLE_STORE_ADMIN));

        ApiException exception = assertThrows(ApiException.class,
                () -> auditLogService.getAllAuditLogs(null, filter()));

        assertEquals(403, exception.status().value());
        verify(auditLogRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unauthenticatedAuditReadIsReportedAsUnauthorized() throws Exception {
        when(userService.getCurrentUser()).thenThrow(new UserException("missing authentication"));

        ApiException exception = assertThrows(ApiException.class,
                () -> auditLogService.getStoreAuditLogs(10L, filter()));

        assertEquals(401, exception.status().value());
        verify(auditLogRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidAuditLogPagingAndTimeRangeNeverReachRepository() throws Exception {
        User admin = user(1L, UserRole.ROLE_ADMIN);
        when(userService.getCurrentUser()).thenReturn(admin);

        ApiException invalidPageSize = assertThrows(ApiException.class,
                () -> auditLogService.getAllAuditLogs(null, new AuditLogFilter(0, 101, null, null, null, null)));
        ApiException invalidRange = assertThrows(ApiException.class,
                () -> auditLogService.getAllAuditLogs(null, new AuditLogFilter(
                        0, 20, null, null,
                        LocalDateTime.of(2026, 9, 2, 0, 0),
                        LocalDateTime.of(2026, 9, 1, 0, 0))));

        assertEquals(400, invalidPageSize.status().value());
        assertEquals(400, invalidRange.status().value());
        verify(auditLogRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    private AuditLogFilter filter() {
        return new AuditLogFilter(0, 50, null, null, null, null);
    }

    private AuditLog auditLog(Store store, User actor, String action) {
        return AuditLog.builder()
                .id(50L)
                .store(store)
                .actor(actor)
                .action(action)
                .entityType("Order")
                .entityId(70L)
                .detail("Đơn hàng mới")
                .createdAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build();
    }

    private Store store(Long id, String brand) {
        Store store = new Store();
        store.setId(id);
        store.setBrand(brand);
        return store;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setFullName("Người dùng test");
        user.setRole(role);
        return user;
    }
}
