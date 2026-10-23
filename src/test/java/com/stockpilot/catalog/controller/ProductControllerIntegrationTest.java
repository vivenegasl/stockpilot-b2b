package com.stockpilot.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.catalog.dto.ProductCreateRequest;
import com.stockpilot.catalog.dto.ProductResponse;
import com.stockpilot.catalog.exception.DuplicateSkuException;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.service.ProductService;
import com.stockpilot.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    @DisplayName("POST /api/v1/products returns 201 Created and Location header on valid input (US-001)")
    void createProduct_validPayload_returns201() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "PLC-SIEM-S71500",
                "Siemens SIMATIC S7-1500",
                "1 MB work memory",
                new BigDecimal("1850000.00"),
                1L
        );

        ProductResponse response = new ProductResponse(
                42L,
                "PLC-SIEM-S71500",
                "Siemens SIMATIC S7-1500",
                "1 MB work memory",
                new BigDecimal("1850000.00"),
                1L,
                "Controladores",
                true,
                0,
                Instant.now(),
                Instant.now()
        );

        when(productService.createProduct(any(ProductCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/products/42")))
                .andExpect(jsonPath("$.id", is(42)))
                .andExpect(jsonPath("$.sku", is("PLC-SIEM-S71500")))
                .andExpect(jsonPath("$.unitPrice", is(1850000.00)));
    }

    @Test
    @DisplayName("POST /api/v1/products with duplicate SKU returns 409 Conflict with RFC 7807 (US-001)")
    void createProduct_duplicateSku_returns409Conflict() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "PLC-SIEM-S71500",
                "Siemens SIMATIC S7-1500",
                null,
                new BigDecimal("1850000.00"),
                1L
        );

        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenThrow(new DuplicateSkuException("PLC-SIEM-S71500"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/duplicate-sku")))
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.detail", containsString("Product SKU 'PLC-SIEM-S71500' already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/products with negative price or blank name returns 400 Bad Request (Bean Validation)")
    void createProduct_invalidConstraints_returns400ValidationFailed() throws Exception {
        // unitPrice negative, name blank, sku invalid regex
        ProductCreateRequest invalidRequest = new ProductCreateRequest(
                "SKU INVALID!!",
                "",
                null,
                new BigDecimal("-50.00"),
                null
        );

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/validation-error")))
                .andExpect(jsonPath("$.invalid_fields.unitPrice").exists())
                .andExpect(jsonPath("$.invalid_fields.name").exists())
                .andExpect(jsonPath("$.invalid_fields.categoryId").exists());
    }

    @Test
    @DisplayName("GET /api/v1/products returns 200 OK with paginated list (US-002)")
    void searchProducts_returns200Paginated() throws Exception {
        ProductResponse p1 = new ProductResponse(1L, "PLC-01", "PLC 1", null, new BigDecimal("100.00"), 1L, "Cat", true, 0, Instant.now(), Instant.now());
        when(productService.searchCatalog(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(p1), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku", is("PLC-01")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} when not found returns 404 Not Found RFC 7807")
    void getProductById_notFound_returns404() throws Exception {
        when(productService.getProductById(eq(999L)))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/resource-not-found")))
                .andExpect(jsonPath("$.title", is("Not Found")));
    }
}
