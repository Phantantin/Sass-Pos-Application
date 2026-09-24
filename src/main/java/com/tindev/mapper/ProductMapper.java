package com.tindev.mapper;

import com.tindev.modal.Category;
import com.tindev.modal.Product;
import com.tindev.modal.Store;
import com.tindev.payload.dto.ProductDTO;

public class ProductMapper {

    public static ProductDTO toDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .mrp(product.getMrp())
                .costPrice(product.getCostPrice())
                .sellingPrice(product.getSellingPrice())
                .brand(product.getBrand())
                .category(product.getCategory() == null ? null : CategoryMapper.toDTO(product.getCategory()))
                .storeId(product.getStore()!=null?product.getStore().getId():null)
                .catalogStatus(product.getCatalogStatus())
                .image(product.getImage())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public static Product toEntity(ProductDTO productDTO,
                                   Store store,
                                   Category category) {
        return Product.builder()
                .name(productDTO.getName())
                .store(store)
                .catalogStatus(productDTO.getCatalogStatus())
                .category(category)
                .sku(productDTO.getSku())
                .description(productDTO.getDescription())
                .mrp(productDTO.getMrp())
                .costPrice(productDTO.getCostPrice() == null ? java.math.BigDecimal.ZERO : productDTO.getCostPrice())
                .sellingPrice(productDTO.getSellingPrice())
                .brand(productDTO.getBrand())
                .image(productDTO.getImage())
                .build();
    }
}
