package com.tindev.service.impl;

import com.tindev.domain.StoreStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.StoreMapper;
import com.tindev.modal.Store;
import com.tindev.modal.StoreContact;
import com.tindev.modal.User;
import com.tindev.payload.dto.StoreDto;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.StoreService;
import com.tindev.service.AuditLogService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public StoreDto createStore(StoreDto dto, User user) {
        if (user.getRole() != UserRole.ROLE_STORE_ADMIN) {
            throw ApiException.forbidden("Chỉ chủ cửa hàng mới có thể gửi yêu cầu tạo cửa hàng");
        }
        Store saved = storeRepository.save(StoreMapper.toEntity(dto, user));
        if (user.getStore() == null) {
            user.setStore(saved);
            userRepository.save(user);
        }
        subscriptionService.initializeTrial(saved);
        auditLogService.record(saved, user, "STORE_CREATED", "STORE", saved.getId(), "Tên cửa hàng: " + saved.getBrand());
        return StoreMapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDto getStoreById(Long id) {
        Store store = findStore(id);
        requireStoreReadAccess(currentUser(), store);
        return StoreMapper.toDTO(store);
    }
    @Override
    @Transactional(readOnly = true)
    public List<StoreDto> getAllStores() {
        if (currentUser().getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.forbidden("Chỉ quản trị viên hệ thống có thể xem tất cả cửa hàng");
        }
        return storeRepository.findAll().stream().map(StoreMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreDto> getManagedStores() {
        User current = currentUser();
        if (current.getRole() != UserRole.ROLE_STORE_ADMIN) {
            throw ApiException.forbidden("Chỉ quản trị cửa hàng có thể xem danh sách cửa hàng được quản lý");
        }
        Map<Long, Store> managed = new LinkedHashMap<>();
        storeRepository.findAllByStoreAdminIdOrderByCreatedAtDesc(current.getId())
                .forEach(store -> managed.put(store.getId(), store));
        if (current.getStore() != null) {
            managed.putIfAbsent(current.getStore().getId(), current.getStore());
        }
        return managed.values().stream().map(StoreMapper::toDTO).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public Store getStoreByAdmin() throws UserException {
        User current = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(current.getId());
        if (store == null) {
            throw new UserException("Tài khoản chưa sở hữu cửa hàng");
        }
        return store;
    }

    @Override
    @Transactional
    public StoreDto updateStore(Long id, StoreDto dto) throws Exception {
        Store store = findStore(id);
        User current = currentUser();
        requireStoreOwnerOrAdmin(current, store);
        store.setBrand(requiredText(dto.getBrand(), "Tên cửa hàng"));
        store.setDescription(trimToNull(dto.getDescription()));
        store.setStoreType(trimToNull(dto.getStoreType()));
        if (dto.getContact() != null) store.setContact(new StoreContact(dto.getContact().getAddress(), dto.getContact().getPhone(), dto.getContact().getEmail()));
        return StoreMapper.toDTO(storeRepository.save(store));
    }

    @Override
    @Transactional
    public void deleteStore(Long id) throws UserException {
        Store store = findStore(id);
        User current = currentUser();
        requireStoreOwnerOrAdmin(current, store);
        subscriptionService.deleteForStore(store);
        storeRepository.delete(store);
    }
    @Override
    @Transactional(readOnly = true)
    public StoreDto getStoreByEmployee() throws UserException {
        User current = userService.getCurrentUser();
        if (current.getStore() != null) return StoreMapper.toDTO(current.getStore());
        if (current.getBranch() != null) return StoreMapper.toDTO(current.getBranch().getStore());
        throw new UserException("Tài khoản chưa được gán cửa hàng");
    }
    @Override
    @Transactional
    public StoreDto moderateStore(Long id, StoreStatus status) {
        if (currentUser().getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.forbidden("Chỉ quản trị viên hệ thống có thể kiểm duyệt cửa hàng");
        }
        if (status == null) {
            throw ApiException.badRequest("Trạng thái cửa hàng là bắt buộc");
        }
        Store store = findStore(id);
        store.setStatus(status);
        Store saved = storeRepository.save(store);
        auditLogService.record(saved, currentUser(), "STORE_STATUS_CHANGED", "STORE", saved.getId(), "Trạng thái: " + status);
        return StoreMapper.toDTO(saved);
    }

    private Store findStore(Long id) {
        return storeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private void requireStoreOwnerOrAdmin(User current, Store store) {
        if (current.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (current.getRole() == UserRole.ROLE_STORE_ADMIN && store.getStoreAdmin() != null
                && store.getStoreAdmin().getId().equals(current.getId())) {
            return;
        }
        if (current.getRole() == UserRole.ROLE_STORE_ADMIN && current.getStore() != null
                && current.getStore().getId().equals(store.getId())) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền quản lý cửa hàng này");
    }

    private void requireStoreReadAccess(User current, Store store) {
        if (current.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        if (current.getStore() != null && current.getStore().getId().equals(store.getId())) {
            return;
        }
        if (current.getRole() == UserRole.ROLE_STORE_ADMIN && store.getStoreAdmin() != null
                && store.getStoreAdmin().getId().equals(current.getId())) {
            return;
        }
        if (current.getBranch() != null && current.getBranch().getStore() != null
                && current.getBranch().getStore().getId().equals(store.getId())) {
            return;
        }
        throw ApiException.forbidden("Bạn không có quyền xem cửa hàng này");
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw ApiException.forbidden("Không thể xác thực người dùng hiện tại");
        }
    }

    private String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(label + " là bắt buộc");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
