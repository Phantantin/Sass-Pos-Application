package com.tindev.repository;

import com.tindev.modal.Product;
import com.tindev.domain.CatalogStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStoreId(Long storeId);
    List<Product> findByStoreIdAndCategoryId(Long storeId, Long categoryId);
    List<Product> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<Product> findByStoreIdAndCategoryIdOrderByCreatedAtDesc(Long storeId, Long categoryId);
    boolean existsByCategoryId(Long categoryId);
    boolean existsBySkuIgnoreCase(String sku);
    List<Product> findByCatalogStatusOrderByCreatedAtDesc(CatalogStatus status);

    @Query("SELECT p FROM Product p WHERE p.catalogStatus = :status OR p.store.id = :storeId ORDER BY p.createdAt DESC")
    List<Product> findInventoryCatalog(@Param("storeId") Long storeId,
                                       @Param("status") CatalogStatus status);

    @Query(
            "SELECT p FROM Product p "+
                    "WHERE p.store.id = :storeId AND("+
                    "LOWER(p.name) LIKE LOWER (CONCAT('%', :query, '%'))"+
                    "Or LOWER(p.brand) LIKE LOWER (CONCAT('%', :query, '%'))"+
                    "Or LOWER(p.sku) LIKE LOWER (CONCAT('%', :query, '%'))"+
                    ")"
    )
    List<Product> searchByKeyword(@Param("storeId") Long storeId,
                                  @Param("query") String keyword);

}
