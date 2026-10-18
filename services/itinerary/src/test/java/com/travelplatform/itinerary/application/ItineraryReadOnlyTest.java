package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.itinerary.api.ItineraryController;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.domain.ItineraryTimeSlot;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryReadOnlyTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final ItineraryService service = new ItineraryService(
            repository, itemRepository, mock(ItineraryEventPublisher.class),
            new ObjectMapper(), mock(InventoryClient.class));

    @Test
    void addAndDeleteReturn409ForConfirmedLockedAndCompleted() {
        for (ItineraryStatus status : new ItineraryStatus[]{
                ItineraryStatus.CONFIRMED, ItineraryStatus.LOCKED, ItineraryStatus.COMPLETED}) {
            Itinerary itinerary = new Itinerary();
            itinerary.setId("it-1");
            itinerary.setUserId("u-1");
            itinerary.setStatus(status);
            when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.of(itinerary));

            BusinessException add = assertThrows(BusinessException.class, () -> service.addItem(
                    "u-1", "it-1", new ItineraryController.AddItemRequest(
                            LocalDate.of(2026, 10, 20), null, null, null,
                            ItineraryTimeSlot.MORNING, "r-1", "s-1")));
            assertEquals(ErrorCode.CONFLICT, add.getErrorCode(), status.name());

            BusinessException delete = assertThrows(BusinessException.class,
                    () -> service.deleteItem("u-1", "it-1", "i-1"));
            assertEquals(ErrorCode.CONFLICT, delete.getErrorCode(), status.name());
        }
    }
}
