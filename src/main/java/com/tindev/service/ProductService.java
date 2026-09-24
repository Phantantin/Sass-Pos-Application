package com.tindev.service;

import com.tindev.domain.CatalogStatus;
import com.tindev.payload.dto.ProductDTO;

import java.util.List;

public interface ProductService {

    ProductDTO createProduct(ProductDTO productDTO);

    ProductDTO updateProduct(Long id, ProductDTO productDTO);

    void deleteProduct(Long id);

    ProductDTO getProductById(Long id);

    List<ProductDTO> getAllProductsByStoreId(Long storeId, Long categoryId);

    List<ProductDTO> searchByKeyword(Long storeId, String keyword);

    List<ProductDTO> getGlobalCatalog();

    List<ProductDTO> getInventoryCatalog(Long storeId);

    ProductDTO moderateCatalogProduct(Long id, CatalogStatus status);
}
