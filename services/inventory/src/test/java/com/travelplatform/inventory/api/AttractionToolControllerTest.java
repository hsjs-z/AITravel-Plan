package com.travelplatform.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.common.api.GlobalExceptionHandler;
import com.travelplatform.inventory.application.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttractionToolControllerTest {

    private final InventoryService inventoryService = mock(InventoryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InventoryController(inventoryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsAttractionPageWithSpecAndAvailability() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 18);
        AttractionToolResponse.AttractionItem item = new AttractionToolResponse.AttractionItem(
                "r-yulong", "玉龙雪山", "云南省", "丽江", "5A", "国家地质公园",
                "雪山景区", new java.math.BigDecimal("4.0"), 100L, false,
                new java.math.BigDecimal("27.096143"), new java.math.BigDecimal("100.179253"),
                "READY", "s-yulong", "成人票", 100L, 200, "/images/yulong.jpg");
        when(inventoryService.attractions(
                eq("丽江"), isNull(), isNull(), eq(date), eq(20), eq(0)))
                .thenReturn(new AttractionToolResponse.AttractionPage(
                        1, 0, 20, false, List.of(item)));

        mockMvc.perform(get("/inventory/attractions")
                        .param("destination", "丽江")
                        .param("date", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].resourceId").value("r-yulong"))
                .andExpect(jsonPath("$.data.items[0].specId").value("s-yulong"))
                .andExpect(jsonPath("$.data.items[0].available").value(200))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("/images/yulong.jpg"));
    }

    @Test
    void missingLocationFilterReturns400() throws Exception {
        when(inventoryService.attractions(isNull(), isNull(), isNull(), isNull(), eq(20), eq(0)))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST,
                        "at least one of destination, province, city is required"));

        mockMvc.perform(get("/inventory/attractions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void invalidLimitReturns400() throws Exception {
        when(inventoryService.attractions(eq("丽江"), isNull(), isNull(), isNull(), eq(0), eq(0)))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST,
                        "limit must be between 1 and 50"));

        mockMvc.perform(get("/inventory/attractions")
                        .param("destination", "丽江")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void invalidOffsetReturns400() throws Exception {
        when(inventoryService.attractions(eq("丽江"), isNull(), isNull(), isNull(), eq(20), eq(-1)))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST,
                        "offset must not be negative"));

        mockMvc.perform(get("/inventory/attractions")
                        .param("destination", "丽江")
                        .param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
