package com.stockpilot.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.inventory.dto.StockAdjustmentRequest;
import com.stockpilot.inventory.dto.StockLevelResponse;
import com.stockpilot.inventory.exception.InvalidStockAdjustmentException;
import com.stockpilot.inventory.service.StockService;
import com.stockpilot.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@Import(GlobalExceptionHandler.class)
class StockControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockService stockService;

    @Test
    @DisplayName("POST /api/v1/stock/adjust with valid request returns 200 OK (US-003)")
    void adjustStock_validRequest_returns200() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                1L, 2L, 10, "Ingreso por importación", "OPERATOR-SCL-01"
        );

        StockLevelResponse response = new StockLevelResponse(
                100L, 1L, "PLC-SIEM-S71500", "Siemens S7-1500",
                2L, "WH-SCL-CENTRAL", "Centro Pudahuel",
                20, 0, 20, Instant.now()
        );

        when(stockService.adjustStock(any(StockAdjustmentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/stock/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.totalQty", is(20)))
                .andExpect(jsonPath("$.availableQty", is(20)))
                .andExpect(jsonPath("$.warehouseCode", is("WH-SCL-CENTRAL")));
    }

    @Test
    @DisplayName("POST /api/v1/stock/adjust with negative delta exceeding available returns 400 Bad Request RFC 7807 (US-003)")
    void adjustStock_invalidDelta_returns400ProblemDetail() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                1L, 2L, -15, "Ajuste erróneo", "OPERATOR-SCL-01"
        );

        when(stockService.adjustStock(any(StockAdjustmentRequest.class)))
                .thenThrow(new InvalidStockAdjustmentException(10, 5, -15));

        mockMvc.perform(post("/api/v1/stock/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/invalid-stock-adjustment")))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.detail", containsString("delta of -15 would reduce total")));
    }

    @Test
    @DisplayName("POST /api/v1/stock/adjust with null required fields returns 400 Bad Request Validation Error")
    void adjustStock_missingFields_returns400ValidationError() throws Exception {
        StockAdjustmentRequest invalidRequest = new StockAdjustmentRequest(
                null, null, null, "", ""
        );

        mockMvc.perform(post("/api/v1/stock/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/validation-error")))
                .andExpect(jsonPath("$.invalid_fields.productId").exists())
                .andExpect(jsonPath("$.invalid_fields.warehouseId").exists())
                .andExpect(jsonPath("$.invalid_fields.quantityDelta").exists())
                .andExpect(jsonPath("$.invalid_fields.reason").exists());
    }

    @Test
    @DisplayName("GET /api/v1/stock/product/{id} returns list of stock levels across warehouses (US-004)")
    void getStockByProduct_returnsStockList() throws Exception {
        StockLevelResponse sl = new StockLevelResponse(
                100L, 1L, "PLC-SIEM-S71500", "Siemens S7-1500",
                2L, "WH-SCL-CENTRAL", "Centro Pudahuel",
                10, 0, 10, Instant.now()
        );

        when(stockService.getStockByProduct(eq(1L))).thenReturn(List.of(sl));

        mockMvc.perform(get("/api/v1/stock/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productSku", is("PLC-SIEM-S71500")))
                .andExpect(jsonPath("$[0].availableQty", is(10)));
    }

    @Test
    @DisplayName("GET /api/v1/stock/product/{id} when product does not exist returns 404 Not Found RFC 7807")
    void getStockByProduct_notFound_returns404() throws Exception {
        when(stockService.getStockByProduct(eq(999L)))
                .thenThrow(new ProductNotFoundException(999L));

        mockMvc.perform(get("/api/v1/stock/product/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type", is("https://api.stockpilot.local/problems/resource-not-found")))
                .andExpect(jsonPath("$.title", is("Not Found")));
    }
}
