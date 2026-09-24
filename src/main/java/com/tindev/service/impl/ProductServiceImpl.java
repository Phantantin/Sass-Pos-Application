package com.tindev.service.impl;

import com.tindev.domain.CatalogStatus;
import com.tindev.domain.UserRole;
import com.tindev.exceptions.ApiException;
import com.tindev.exceptions.UserException;
import com.tindev.mapper.ProductMapper;
import com.tindev.modal.Category;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.modal.User;
import com.tindev.payload.dto.ProductDTO;
import com.tindev.repository.CategoryRepository;
import com.tindev.repository.InventoryMovementRepository;
import com.tindev.repository.InventoryRepository;
import com.tindev.repository.ProductRepository;
import com.tindev.repository.StoreRepository;
import com.tindev.service.ProductService;
import com.tindev.service.SubscriptionService;
import com.tindev.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    @Override
    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        if (dto.getStoreId() == null || dto.getCategoryId() == null) {
            throw ApiException.badRequest("storeId và categoryId là bắt buộc khi tạo sản phẩm");
        }
        Store store = requireStore(dto.getStoreId());
        User actor = currentUser();
        assertCanManageCatalog(actor, store);
        subscriptionService.assertCanCreateProduct(store);
        Category category = requireCategory(dto.getCategoryId());
        assertCategoryBelongsToStore(category, store);

        String sku = normalizeRequired(dto.getSku(), "SKU");
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU đã tồn tại");
        }
        validatePrices(dto);
        Product product = ProductMapper.toEntity(dto, store, category);
        product.setCatalogStatus(actor.getRole() == UserRole.ROLE_ADMIN
                ? CatalogStatus.APPROVED : CatalogStatus.PENDING);
        applyEditableFields(product, dto, sku);
        return ProductMapper.toDTO(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO dto) {
        Product product = requireProduct(id);
        User actor = currentUser();
        assertCanManageCatalog(actor, product.getStore());

        String sku = normalizeRequired(dto.getSku(), "SKU");
        if (!product.getSku().equalsIgnoreCase(sku) && productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU đã tồn tại");
        }
        validatePrices(dto);
        if (dto.getCategoryId() != null) {
            Category category = requireCategory(dto.getCategoryId());
            assertCategoryBelongsToStore(category, product.getStore());
            product.setCategory(category);
        }
        applyEditableFields(product, dto, sku);
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            product.setCatalogStatus(CatalogStatus.PENDING);
        }
        return ProductMapper.toDTO(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = requireProduct(id);
        assertCanManageCatalog(currentUser(), product.getStore());
        if (inventoryRepository.existsByProductId(product.getId())) {
            throw ApiException.conflict("Không thể xóa sản phẩm đang có bản ghi tồn kho");
        }
        if (inventoryMovementRepository.existsByProductId(product.getId())) {
            throw ApiException.conflict("Không thể xóa sản phẩm đã có lịch sử biến động tồn kho");
        }
        productRepository.delete(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long id) {
        Product product = requireProduct(id);
        if (product.getCatalogStatus() != CatalogStatus.APPROVED) {
            assertCanReadStore(currentUser(), product.getStore());
        }
        return ProductMapper.toDTO(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> getAllProductsByStoreId(Long storeId, Long categoryId) {
        Store store = requireStore(storeId);
        assertCanReadStore(currentUser(), store);
        if (categoryId != null) {
            Category category = requireCategory(categoryId);
            assertCategoryBelongsToStore(category, store);
            return productRepository.findByStoreIdAndCategoryIdOrderByCreatedAtDesc(storeId, categoryId).stream().map(ProductMapper::toDTO).toList();
        }
        return productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream().map(ProductMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> searchByKeyword(Long storeId, String keyword) {
        Store store = requireStore(storeId);
        assertCanReadStore(currentUser(), store);
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) {
            return productRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream().map(ProductMapper::toDTO).toList();
        }
        return productRepository.searchByKeyword(storeId, query).stream().map(ProductMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> getGlobalCatalog() {
        return productRepository.findByCatalogStatusOrderByCreatedAtDesc(CatalogStatus.APPROVED)
                .stream().map(ProductMapper::toDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> getInventoryCatalog(Long storeId) {
        Store store = requireStore(storeId);
        assertCanReadStore(currentUser(), store);
        return productRepository.findInventoryCatalog(storeId, CatalogStatus.APPROVED)
                .stream().map(ProductMapper::toDTO).toList();
    }

    @Override
    @Transactional
    public ProductDTO moderateCatalogProduct(Long id, CatalogStatus status) {
        User actor = currentUser();
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw ApiException.forbidden("Chỉ HQ có thể duyệt sản phẩm dùng chung");
        }
        if (status == null) {
            throw ApiException.badRequest("Trạng thái duyệt là bắt buộc");
        }
        Product product = requireProduct(id);
        product.setCatalogStatus(status);
        return ProductMapper.toDTO(productRepository.save(product));
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
    }

    private Store requireStore(Long id) {
        if (id == null) {
            throw ApiException.badRequest("storeId là bắt buộc");
        }
        return storeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy cửa hàng"));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy danh mục"));
    }

    private void assertCategoryBelongsToStore(Category category, Store store) {
        if (category.getStore() == null || !Objects.equals(category.getStore().getId(), store.getId())) {
            throw ApiException.badRequest("Danh mục phải thuộc cùng cửa hàng với sản phẩm");
        }
    }

    private User currentUser() {
        try {
            return userService.getCurrentUser();
        } catch (UserException exception) {
            throw ApiException.forbidden("Không xác định được tài khoản hiện tại");
        }
    }

    private void validatePrices(ProductDTO dto) {
        if (dto.getSellingPrice() == null || dto.getSellingPrice().compareTo(BigDecimal.ZERO) < 0
                || (dto.getMrp() != null && dto.getMrp().compareTo(BigDecimal.ZERO) < 0)
                || (dto.getCostPrice() != null && dto.getCostPrice().compareTo(BigDecimal.ZERO) < 0)) {
            throw ApiException.badRequest("Giá sản phẩm không được âm");
        }
    }

    private void applyEditableFields(Product product, ProductDTO dto, String sku) {
        product.setName(normalizeRequired(dto.getName(), "Tên sản phẩm"));
        product.setSku(sku);
        product.setDescription(trimToNull(dto.getDescription()));
        product.setMrp(dto.getMrp());
        product.setCostPrice(dto.getCostPrice() == null ? BigDecimal.ZERO : dto.getCostPrice());
        product.setSellingPrice(dto.getSellingPrice());
        product.setBrand(trimToNull(dto.getBrand()));
        product.setImage(trimToNull(dto.getImage()));
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw ApiException.badRequest(label + " là bắt buộc");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private void assertCanManageCatalog(User user, Store store) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        boolean isOwner = user.getRole() == UserRole.ROLE_STORE_ADMIN && belongsToStore(user, store);
        boolean isStoreManager = user.getRole() == UserRole.ROLE_STORE_MANAGER && belongsToStore(user, store);
        if (!isOwner && !isStoreManager) {
            throw ApiException.forbidden("Bạn không có quyền quản lý sản phẩm của cửa hàng này");
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
            throw ApiException.forbidden("Bạn không có quyền xem sản phẩm của cửa hàng này");
        }
    }

    private boolean belongsToStore(User user, Store store) {
        if (user.getStore() != null && Objects.equals(user.getStore().getId(), store.getId())) return true;
        return store.getStoreAdmin() != null && Objects.equals(store.getStoreAdmin().getId(), user.getId());
    }
}
