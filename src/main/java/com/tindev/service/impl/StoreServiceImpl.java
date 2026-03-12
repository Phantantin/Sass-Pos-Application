package com.tindev.service.impl;

import com.tindev.domain.StoreStatus;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.StoreMapper;
import com.tindev.modal.Store;
import com.tindev.modal.StoreContact;
import com.tindev.modal.User;
import com.tindev.payload.dto.StoreDto;
import com.tindev.repository.StoreRepository;
import com.tindev.service.StoreService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    public StoreDto createStore(StoreDto storeDto, User user) {
        Store store = StoreMapper.toEntity(storeDto, user);
        return StoreMapper.toDTO(storeRepository.save(store));
    }

    @Override
    public StoreDto getStoreById(Long id) throws Exception {
        Store store = storeRepository.findById(id).orElseThrow(
                ()-> new Exception("Store not found...")
        );
        return StoreMapper.toDTO(storeRepository.save(store));
    }

    @Override
    public List<StoreDto> getAllStores() {
        List<Store> dtos = storeRepository.findAll();
        return dtos.stream().map(StoreMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public Store getStoreByAdmin() throws UserException {
        User admin = userService.getCurrentUser();
        return storeRepository.findByStoreAdminId(admin.getId());
    }

    @Override
    public StoreDto updateStore(Long id, StoreDto storeDto) throws Exception {
//        User currentUser = userService.getCurrentUser();
//        Store existing = storeRepository.findByStoreAdminId(currentUser.getId());
//        if(existing != null) {
//            throw new Exception("Store not found...");
//
//        }

        // 1. Tìm Store theo ID truyền vào từ URL
        Store existing = storeRepository.findById(id).orElseThrow(
                () -> new Exception("Store not found with id: " + id)
        );

        // 2. (Tùy chọn) Kiểm tra xem user hiện tại có phải chủ của Store này không
        User currentUser = userService.getCurrentUser();
        if (!existing.getStoreAdmin().getId().equals(currentUser.getId())) {
            throw new Exception("You don't have permission to update this store");
        }

        existing.setBrand(storeDto.getBrand());
        existing.setDescription(storeDto.getDescription());
        if(storeDto.getContact() != null) {
            StoreContact contact = StoreContact.builder()
                    .address(storeDto.getContact().getAddress())
                    .email(storeDto.getContact().getEmail())
                    .build();
            existing.setContact(contact);
        }
        Store updatedStore = storeRepository.save(existing);
        return StoreMapper.toDTO(updatedStore);
    }

    @Override
    public void deleteStore(Long id) throws UserException {
        Store store = getStoreByAdmin();
        storeRepository.delete(store);
    }

    @Override
    public StoreDto getStoreByEmployee() throws UserException {
        User currentUser = userService.getCurrentUser();
        if(currentUser == null) {
            throw new UserException("You don't have permission to access this store");
        }
        return StoreMapper.toDTO(currentUser.getStore());
    }

    @Override
    public StoreDto moderateStore(Long id, StoreStatus status) throws Exception {
        Store store = storeRepository.findById(id).orElseThrow(
                ()-> new Exception("Store not found...")
        );
        store.setStatus(status);
        Store updatedStore = storeRepository.save(store);
        return StoreMapper.toDTO(updatedStore);
    }
}
