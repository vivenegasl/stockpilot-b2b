package com.stockpilot.catalog.repository;

import com.stockpilot.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE UPPER(p.sku) = UPPER(:sku)")
    Optional<Product> findBySkuWithCategory(@Param("sku") String sku);

    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category " +
            "WHERE (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND p.active = true",
            countQuery = "SELECT COUNT(p) FROM Product p " +
                    "WHERE (:categoryId IS NULL OR p.category.id = :categoryId) " +
                    "AND (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))) " +
                    "AND p.active = true")
    Page<Product> searchCatalog(@Param("query") String query,
                                @Param("categoryId") Long categoryId,
                                Pageable pageable);
}
