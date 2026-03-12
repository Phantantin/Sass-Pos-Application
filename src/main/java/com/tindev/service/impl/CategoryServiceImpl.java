package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.CategoryMapper;
import com.tindev.modal.Category;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.CategoryDTO;
import com.tindev.repository.CategoryRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.CategoryService;
import com.tindev.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final StoreRepository storeRepository;

    @Override
    public CategoryDTO createCategory(CategoryDTO dto) throws Exception {
        User user = userService.getCurrentUser();

        Store store = storeRepository.findById(dto.getStoreId()).orElseThrow(
                () -> new Exception("Store not found")
        );

        Category category = Category.builder()
                .store(store)
                .name(dto.getName())
                .build();

        checkAuthority(user, category.getStore());

        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDTO> getAllCategoriesByStore(Long storeId) {
        List<Category> categories = categoryRepository.findByStoreId(storeId);

        return categories.stream().map(CategoryMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) throws Exception {
        Category category = categoryRepository.findById(id).orElseThrow(
                ()-> new Exception("Category not exit")
        );
        User user = userService.getCurrentUser();

        category.setName(dto.getName());
        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public void deleteCategory(Long id) throws Exception {
        Category category = categoryRepository.findById(id).orElseThrow(
                ()-> new Exception("Category not exit")
        );
        User user = userService.getCurrentUser();

        checkAuthority(user, category.getStore());

        categoryRepository.delete(category);
    }

    private void checkAuthority(User user, Store store) throws Exception {
        boolean isAdmin = user.getRole().equals(UserRole.ROLE_STORE_ADMIN);
        boolean isManager = user.getRole().equals(UserRole.ROLE_STORE_MANAGER);
        boolean isSameStore = user.equals(store.getStoreAdmin());
        if(!(isAdmin && isSameStore) && !isManager) {
            throw new Exception("You don't have permission to manage this category");
        }
    }
}
