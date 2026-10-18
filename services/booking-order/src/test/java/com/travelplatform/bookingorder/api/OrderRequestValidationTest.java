package com.travelplatform.bookingorder.api;

import com.travelplatform.bookingorder.application.OrderService;
import com.travelplatform.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderRequestValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(mock(OrderService.class)))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void emptyItemsReturns400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itineraryId\":\"it-1\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void nullDateReturns400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itineraryId":"it-1","items":[
                                  {"resourceId":"r-1","specId":"s-1","date":null,
                                   "quantity":1,"unitPrice":100}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void invalidQuantityAndPriceReturn400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itineraryId":"it-1","items":[
                                  {"resourceId":"r-1","specId":"s-1","date":"2026-10-20",
                                   "quantity":0,"unitPrice":0}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
