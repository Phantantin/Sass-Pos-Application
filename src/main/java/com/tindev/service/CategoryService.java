package com.tindev.service;

import com.tindev.payload.dto.CategoryDTO;

import java.util.List;

public interface CategoryService {

    CategoryDTO createCategory(CategoryDTO dto);
    List<CategoryDTO> getAllCategoriesByStore(Long storeId);
    CategoryDTO updateCategory(Long id, CategoryDTO dto);
    void deleteCategory(Long id);
}
