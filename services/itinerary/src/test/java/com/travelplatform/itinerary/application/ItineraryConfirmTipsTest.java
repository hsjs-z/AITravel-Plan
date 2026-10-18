package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItem;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import com.travelplatform.itinerary.infrastructure.OrderClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryConfirmTipsTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final ItineraryTipsService tipsService = mock(ItineraryTipsService.class);
    private final ItineraryService service = new ItineraryService(
            repository,
            itemRepository,
            mock(ItineraryEventPublisher.class),
            new ObjectMapper(),
            mock(InventoryClient.class),
            mock(OrderClient.class),
            tipsService);

    @Test
    void confirmGeneratesTipsFromFinalItems() {
        Itinerary itinerary = new Itinerary();
        itinerary.setId("it-1");
        itinerary.setUserId("u-1");
        itinerary.setDestination("西安");
        itinerary.setStartDate(LocalDate.of(2026, 10, 18));
        itinerary.setEndDate(LocalDate.of(2026, 10, 19));
        itinerary.setStatus(ItineraryStatus.DRAFT);
        itinerary.setTips("[\"old\"]");
        when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.of(itinerary));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByItineraryIdOrderByDayDateAscTimeSlotAscSortOrderAsc("it-1"))
                .thenReturn(List.of(item("大雁塔"), item("西安城墙"), item("大雁塔")));

        service.confirm("u-1", "it-1");

        verify(tipsService).generateAsync("it-1", "西安", 2, List.of("大雁塔", "西安城墙"));
    }

    private ItineraryItem item(String name) {
        ItineraryItem item = new ItineraryItem();
        item.setName(name);
        item.setDescription(name);
        return item;
    }
}
