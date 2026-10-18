package com.travelplatform.itinerary.api;

import com.travelplatform.itinerary.application.ItineraryService;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItineraryControllerTest {

    private final ItineraryService service = mock(ItineraryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ItineraryController(service))
            .build();

    @Test
    void listReturnsOnlyTheCurrentUsersItineraries() throws Exception {
        Itinerary itinerary = new Itinerary();
        itinerary.setId("it-1");
        itinerary.setUserId("u-1");
        itinerary.setDestination("丽江");
        itinerary.setStartDate(LocalDate.of(2026, 10, 20));
        itinerary.setEndDate(LocalDate.of(2026, 10, 22));
        itinerary.setPartySize(2);
        itinerary.setStatus(ItineraryStatus.CONFIRMED);
        itinerary.setHasOrder(true);
        itinerary.setCreatedAt(Instant.parse("2026-10-18T10:00:00Z"));
        when(service.list("u-1")).thenReturn(List.of(itinerary));
        when(service.orderSummaries("u-1", List.of("it-1")))
                .thenReturn(Map.of("it-1", new ItineraryService.OrderCounts(1, 0)));

        mockMvc.perform(get("/itineraries").header("X-User-Id", "u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("it-1"))
                .andExpect(jsonPath("$.data[0].destination").value("丽江"))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data[0].hasOrder").value(true))
                .andExpect(jsonPath("$.data[0].activeOrderCount").value(1))
                .andExpect(jsonPath("$.data[0].completedOrderCount").value(0));

        verify(service).list("u-1");
    }
}
