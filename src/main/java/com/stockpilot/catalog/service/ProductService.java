package com.stockpilot.catalog.service;

import com.stockpilot.catalog.dto.ProductCreateRequest;
import com.stockpilot.catalog.dto.ProductResponse;
import com.stockpilot.catalog.dto.ProductUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse createProduct(ProductCreateRequest request);

    ProductResponse getProductById(Long id);

    ProductResponse getProductBySku(String sku);

    ProductResponse updateProduct(Long id, ProductUpdateRequest request);

    Page<ProductResponse> searchCatalog(String query, Long categoryId, Pageable pageable);
}
