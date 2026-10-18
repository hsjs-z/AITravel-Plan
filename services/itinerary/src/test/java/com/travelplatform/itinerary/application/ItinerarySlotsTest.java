package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.itinerary.api.ItineraryController;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItem;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryItemSource;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryTimeSlot;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import com.travelplatform.itinerary.infrastructure.OrderClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItinerarySlotsTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final ItineraryEventPublisher events = mock(ItineraryEventPublisher.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final ItineraryService service =
            new ItineraryService(repository, itemRepository, events, new ObjectMapper(), inventoryClient,
                    mock(OrderClient.class));

    @Test
    void persistsSlotOrderAndWarnings() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ItineraryController.CreateItineraryRequest request =
                new ItineraryController.CreateItineraryRequest(
                        "西安",
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 20),
                        2,
                        1000L,
                        List.of(new ItineraryController.ItemRequest(
                                LocalDate.of(2026, 10, 20),
                                "兵马俑",
                                "兵马俑",
                                new java.math.BigDecimal("4"),
                                120L,
                                new java.math.BigDecimal("34.384"),
                                new java.math.BigDecimal("109.278"),
                                "ATTRACTION",
                                "r-xian",
                                "s-xian",
                                ItineraryItemSource.AI,
                                ItineraryTimeSlot.MORNING,
                                1)),
                        List.of("第1天：超长景点超出当日节奏容量，未安排"));

        Itinerary itinerary = service.create("u-1", request);

        ArgumentCaptor<ItineraryItem> itemCaptor = ArgumentCaptor.forClass(ItineraryItem.class);
        verify(itemRepository).save(itemCaptor.capture());
        assertEquals(ItineraryTimeSlot.MORNING, itemCaptor.getValue().getTimeSlot());
        assertEquals(1, itemCaptor.getValue().getSortOrder());
        assertEquals(List.of("第1天：超长景点超出当日节奏容量，未安排"),
                service.warnings(itinerary));
    }
}
