package com.tindev.repository;

import com.tindev.modal.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByStoreAdminIdOrderByCreatedAtDesc(Long adminId);

    default Store findByStoreAdminId(Long adminId) {
        List<Store> stores = findAllByStoreAdminIdOrderByCreatedAtDesc(adminId);
        return stores.isEmpty() ? null : stores.get(0);
    }

}
