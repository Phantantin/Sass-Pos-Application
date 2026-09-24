package com.tindev.controller;

import com.tindev.domain.CatalogStatus;
import com.tindev.payload.dto.ProductDTO;
import com.tindev.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER')")
    public ResponseEntity<ProductDTO> create(@Valid @RequestBody ProductDTO productDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(productDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/store/{storeId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<List<ProductDTO>> getByStoreId(@PathVariable Long storeId,
                                                         @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(productService.getAllProductsByStoreId(storeId, categoryId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER')")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id, @Valid @RequestBody ProductDTO productDTO) {
        return ResponseEntity.ok(productService.updateProduct(id, productDTO));
    }

    @GetMapping("/store/{storeId}/search")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER', 'ROLE_BRANCH_MANAGER', 'ROLE_BRANCH_CASHIER')")
    public ResponseEntity<List<ProductDTO>> searchByKeyword(@PathVariable Long storeId,
                                                            @RequestParam @NotBlank(message = "Từ khóa tìm kiếm là bắt buộc") String keyword) {
        return ResponseEntity.ok(productService.searchByKeyword(storeId, keyword));
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductDTO>> getGlobalCatalog() {
        return ResponseEntity.ok(productService.getGlobalCatalog());
    }

    @GetMapping("/inventory-catalog/store/{storeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProductDTO>> getInventoryCatalog(@PathVariable Long storeId) {
        return ResponseEntity.ok(productService.getInventoryCatalog(storeId));
    }

    @PutMapping("/{id}/catalog-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> moderateCatalogProduct(@PathVariable Long id,
                                                              @RequestParam CatalogStatus status) {
        return ResponseEntity.ok(productService.moderateCatalogProduct(id, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_STORE_ADMIN', 'ROLE_STORE_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
