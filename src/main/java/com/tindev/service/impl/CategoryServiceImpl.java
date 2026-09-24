package com.tindev.service.impl;

import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.CategoryMapper;
import com.tindev.modal.Category;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.CategoryDTO;
import com.tindev.repository.CategoryRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.CategoryService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    @Override
    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (dto.getStoreId() == null) {
            throw ApiException.badRequest("storeId là bắt buộc khi tạo danh mục");
        }
        Store store = requireStore(dto.getStoreId());
        assertCanManageCatalog(currentUser(), store);

        String name = normalizeName(dto.getName());
        if (categoryRepository.existsByStoreIdAndNameIgnoreCase(store.getId(), name)) {
            throw ApiException.conflict("Tên danh mục đã tồn tại trong cửa hàng này");
        }

        Category saved = categoryRepository.save(Category.builder()
                .store(store)
                .name(name)
                .build());
        return CategoryMapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategoriesByStore(Long storeId) {
        Store store = requireStore(storeId);
        assertCanReadStore(currentUser(), store);
        return categoryRepository.findByStoreId(storeId).stream().map(CategoryMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = requireCategory(id);
        assertCanManageCatalog(currentUser(), category.getStore());

        String name = normalizeName(dto.getName());
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsByStoreIdAndNameIgnoreCase(category.getStore().getId(), name)) {
            throw ApiException.conflict("Tên danh mục đã tồn tại trong cửa hàng này");
        }
        category.setName(name);
        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = requireCategory(id);
        assertCanManageCatalog(currentUser(), category.getStore());
        if (productRepository.existsByCategoryId(category.getId())) {
            throw ApiException.conflict("Không thể xóa danh mục đang có sản phẩm");
        }
        categoryRepository.delete(category);
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục"));
    }

    private Store requireStore(Long id) {
        if (id == null) {
            throw ApiException.badRequest("storeId là bắt buộc");
        }
        return storeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw ApiException.forbidden("Không xác định được tài khoản hiện tại");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw ApiException.badRequest("Tên danh mục là bắt buộc");
        }
        return name.trim();
    }

    private void assertCanManageCatalog(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean isOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(user, store);
        if (!isOwner) {
            throw ApiException.forbidden("Bạn không có quyền quản lý danh mục của cửa hàng này");
        }
    }

    private void assertCanReadStore(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean isOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(user, store);
        boolean inStore = (user.getRole() == UserRole.ROLE_STORE_MANAGER && belongsToStore(user, store))
                || ((user.getRole() == UserRole.ROLE_BRANCH_MANAGER || user.getRole() == UserRole.ROLE_BRANCH_CASHIER)
                && user.getBranch() != null
                && user.getBranch().getStore() != null
                && Objects.equals(user.getBranch().getStore().getId(), store.getId()));
        if (!isOwner && !inStore) {
            throw ApiException.forbidden("Bạn không có quyền xem danh mục của cửa hàng này");
        }
    }

    private boolean belongsToStore(User user, Store store) {
        if (user.getStore() != null && Objects.equals(user.getStore().getId(), store.getId())) return true;
        return store.getStoreAdmin() != null && Objects.equals(store.getStoreAdmin().getId(), user.getId());
    }
}
