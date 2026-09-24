package com.tindev.controller;

import com.tindev.domain.StoreStatus;
import com.tindev.modal.User;
import com.tindev.payload.dto.StoreDto;
import com.tindev.service.StoreService;
import com.tindev.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stores")
public class StoreController {
    private final StoreService storeService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<StoreDto> createStore(@Valid @RequestBody StoreDto storeDto) throws Exception {
        User user = userService.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(storeService.createStore(storeDto, user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StoreDto>> getAllStores() {
        return ResponseEntity.ok(storeService.getAllStores());
    }

    @GetMapping("/managed")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<List<StoreDto>> getManagedStores() {
        return ResponseEntity.ok(storeService.getManagedStores());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    public ResponseEntity<StoreDto> getStoreByAdmin() throws Exception {
        return ResponseEntity.ok(storeService.getStoreByEmployee());
    }

    @GetMapping("/employee")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StoreDto> getStoreByEmployee() throws Exception {
        return ResponseEntity.ok(storeService.getStoreByEmployee());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<StoreDto> updateStore(@PathVariable Long id, @Valid @RequestBody StoreDto storeDto) throws Exception {
        return ResponseEntity.ok(storeService.updateStore(id, storeDto));
    }

    @PutMapping("/{id}/moderate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StoreDto> moderateStore(@PathVariable Long id, @RequestParam StoreStatus status) throws Exception {
        return ResponseEntity.ok(storeService.moderateStore(id, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StoreDto> getStoreById(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(storeService.getStoreById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<Void> deleteStore(@PathVariable Long id) throws Exception {
        storeService.deleteStore(id);
        return ResponseEntity.noContent().build();
    }
}
