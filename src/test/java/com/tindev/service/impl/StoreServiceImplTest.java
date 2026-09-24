package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.modal.Store;
import com.tindev.modal.StoreContact;
import com.tindev.modal.User;
import com.tindev.payload.dto.StoreDto;
import com.tindev.repository.StoreRepository;
import com.tindev.repository.UserRepository;
import com.tindev.service.AuditLogService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private StoreServiceImpl storeService;

    @Test
    void storeAdminCanCreateASecondStoreWithoutReplacingDefaultStore() {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store firstStore = store(10L, "Cửa hàng đầu tiên", owner);
        owner.setStore(firstStore);
        StoreDto request = request("Cửa hàng thứ hai");
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> {
            Store saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        StoreDto created = storeService.createStore(request, owner);

        assertEquals(20L, created.getId());
        assertEquals("Cửa hàng thứ hai", created.getBrand());
        assertSame(firstStore, owner.getStore());
        verify(userRepository, never()).save(owner);
        verify(subscriptionService).initializeTrial(any(Store.class));
        verify(auditLogService).record(any(Store.class), any(User.class),
                org.mockito.ArgumentMatchers.eq("STORE_CREATED"),
                org.mockito.ArgumentMatchers.eq("STORE"),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.contains("Cửa hàng thứ hai"));
    }

    @Test
    void managedStoresReturnsEveryOwnedStoreAndAssignedDefaultWithoutDuplicates() throws Exception {
        User owner = user(1L, UserRole.ROLE_STORE_ADMIN);
        Store first = store(10L, "Store A", owner);
        Store second = store(20L, "Store B", owner);
        owner.setStore(first);
        when(userService.getCurrentUser()).thenReturn(owner);
        when(storeRepository.findAllByStoreAdminIdOrderByCreatedAtDesc(owner.getId()))
                .thenReturn(List.of(second, first));

        List<StoreDto> result = storeService.getManagedStores();

        assertEquals(List.of(20L, 10L), result.stream().map(StoreDto::getId).toList());
    }

    private StoreDto request(String brand) {
        StoreDto dto = new StoreDto();
        dto.setBrand(brand);
        dto.setStoreType("Bán lẻ");
        dto.setContact(new StoreContact("Đà Nẵng", "0900000000", "store@example.com"));
        return dto;
    }

    private Store store(Long id, String brand, User owner) {
        Store store = new Store();
        store.setId(id);
        store.setBrand(brand);
        store.setStoreAdmin(owner);
        return store;
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
