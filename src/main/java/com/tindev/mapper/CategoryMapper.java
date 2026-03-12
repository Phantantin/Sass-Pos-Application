package com.tindev.mapper;

import com.tindev.modal.Category;
import com.tindev.payload.dto.CategoryDTO;

public class CategoryMapper {
    public static CategoryDTO toDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .storeId(category.getStore()!=null?category.getStore().getId(): null)
                .build();
    }
}
