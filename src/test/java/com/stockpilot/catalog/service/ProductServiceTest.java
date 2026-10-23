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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testCategory = new Category("Controladores Industriales", "PLCs y Autómatas");
        testCategory.setId(1L);

        testProduct = new Product(
                "PLC-SIEM-S71500",
                "Siemens SIMATIC S7-1500 CPU",
                "1 MB código",
                new BigDecimal("1850000.00"),
                testCategory
        );
        testProduct.setId(10L);
    }

    @Test
    @DisplayName("US-001: Successfully creates a new product SKU when SKU is unique and category exists")
    void createProduct_success() {
        // Given
        ProductCreateRequest request = new ProductCreateRequest(
                "PLC-SIEM-S71500",
                "Siemens SIMATIC S7-1500 CPU",
                "1 MB código",
                new BigDecimal("1850000.00"),
                1L
        );

        when(productRepository.existsBySkuIgnoreCase("PLC-SIEM-S71500")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When
        ProductResponse response = productService.createProduct(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.sku()).isEqualTo("PLC-SIEM-S71500");
        assertThat(response.categoryName()).isEqualTo("Controladores Industriales");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("US-001: Throws DuplicateSkuException (409 Conflict) when SKU already exists in catalog")
    void createProduct_duplicateSku_throwsConflict() {
        // Given
        ProductCreateRequest request = new ProductCreateRequest(
                "PLC-SIEM-S71500",
                "Siemens S7-1500",
                null,
                new BigDecimal("1850000.00"),
                1L
        );

        when(productRepository.existsBySkuIgnoreCase("PLC-SIEM-S71500")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("PLC-SIEM-S71500");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-001: Throws CategoryNotFoundException when category does not exist")
    void createProduct_categoryNotFound_throwsNotFound() {
        // Given
        ProductCreateRequest request = new ProductCreateRequest(
                "PLC-SIEM-S71500",
                "Siemens S7-1500",
                null,
                new BigDecimal("1850000.00"),
                999L
        );

        when(productRepository.existsBySkuIgnoreCase("PLC-SIEM-S71500")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(CategoryNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-002: Retrieves product by exact SKU successfully")
    void getProductBySku_found() {
        // Given
        when(productRepository.findBySkuWithCategory("PLC-SIEM-S71500")).thenReturn(Optional.of(testProduct));

        // When
        ProductResponse response = productService.getProductBySku("PLC-SIEM-S71500");

        // Then
        assertThat(response.sku()).isEqualTo("PLC-SIEM-S71500");
        assertThat(response.name()).isEqualTo("Siemens SIMATIC S7-1500 CPU");
    }

    @Test
    @DisplayName("US-002: Throws ProductNotFoundException when SKU is not found")
    void getProductBySku_notFound_throwsException() {
        // Given
        when(productRepository.findBySkuWithCategory("UNKNOWN-SKU")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.getProductBySku("UNKNOWN-SKU"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("US-002: Paginates catalog search with query and category filter")
    void searchCatalog_paginatesCorrectly() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(testProduct), pageable, 1);

        when(productRepository.searchCatalog(eq("Siemens"), eq(1L), eq(pageable))).thenReturn(page);

        // When
        Page<ProductResponse> result = productService.searchCatalog("Siemens", 1L, pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().sku()).isEqualTo("PLC-SIEM-S71500");
    }

    @Test
    @DisplayName("Updates product metadata successfully")
    void updateProduct_success() {
        // Given
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Siemens S7-1500 Updated",
                "Nueva especificación",
                new BigDecimal("1900000.00"),
                1L,
                true
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When
        ProductResponse response = productService.updateProduct(10L, updateRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(testProduct.getName()).isEqualTo("Siemens S7-1500 Updated");
        verify(productRepository).save(testProduct);
    }
}
