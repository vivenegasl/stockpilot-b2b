package com.stockpilot.catalog.service;

import com.stockpilot.catalog.domain.Category;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.dto.ProductCreateRequest;
import com.stockpilot.catalog.dto.ProductResponse;
import com.stockpilot.catalog.dto.ProductUpdateRequest;
import com.stockpilot.catalog.exception.CategoryNotFoundException;
import com.stockpilot.catalog.exception.DuplicateSkuException;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.repository.CategoryRepository;
import com.stockpilot.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Objects.requireNonNull(request, "ProductCreateRequest must not be null");

        String normalizedSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            log.warn("Attempted to create duplicate SKU: {}", normalizedSku);
            throw new DuplicateSkuException(normalizedSku);
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        Product product = new Product(
                normalizedSku,
                request.name(),
                request.technicalSpec(),
                request.unitPrice(),
                category
        );

        Product saved = productRepository.save(product);
        log.info("Successfully registered new SKU in catalog: id={}, sku={}", saved.getId(), saved.getSku());
        return ProductResponse.from(saved);
    }

    @Override
    public ProductResponse getProductById(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Override
    public ProductResponse getProductBySku(String sku) {
        return productRepository.findBySkuWithCategory(sku.trim())
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(sku.trim()));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Objects.requireNonNull(request, "ProductUpdateRequest must not be null");

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        product.setName(request.name());
        product.setTechnicalSpec(request.technicalSpec());
        product.setUnitPrice(request.unitPrice());
        product.setCategory(category);
        product.setActive(request.active());

        Product updated = productRepository.save(product);
        log.info("Successfully updated product id={}, version={}", updated.getId(), updated.getVersion());
        return ProductResponse.from(updated);
    }

    @Override
    public Page<ProductResponse> searchCatalog(String query, Long categoryId, Pageable pageable) {
        return productRepository.searchCatalog(query, categoryId, pageable)
                .map(ProductResponse::from);
    }
}
