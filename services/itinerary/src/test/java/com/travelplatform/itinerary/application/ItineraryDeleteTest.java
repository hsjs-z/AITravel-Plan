package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import com.travelplatform.itinerary.infrastructure.OrderClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryDeleteTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final OrderClient orderClient = mock(OrderClient.class);
    private final ItineraryService service = new ItineraryService(
            repository,
            itemRepository,
            mock(ItineraryEventPublisher.class),
            new ObjectMapper(),
            mock(InventoryClient.class),
            orderClient,
            mock(ItineraryTipsService.class));

    @Test
    void draftCanBeDeleted() {
        prime(ItineraryStatus.DRAFT, 0, 0);

        service.delete("u-1", "it-1");

        verify(itemRepository).deleteByItineraryId("it-1");
        verify(repository).delete(any(Itinerary.class));
    }

    @Test
    void confirmedWithoutActiveOrdersCanBeDeletedEvenAfterCompletedOrder() {
        prime(ItineraryStatus.CONFIRMED, 0, 1);

        service.delete("u-1", "it-1");

        verify(itemRepository).deleteByItineraryId("it-1");
        verify(repository).delete(any(Itinerary.class));
    }

    @Test
    void confirmedWithActiveOrderIsRejected() {
        prime(ItineraryStatus.CONFIRMED, 1, 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete("u-1", "it-1"));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(itemRepository, never()).deleteByItineraryId("it-1");
        verify(repository, never()).delete(any(Itinerary.class));
    }

    @Test
    void lockedAndCompletedItinerariesAreRejected() {
        for (ItineraryStatus status : List.of(ItineraryStatus.LOCKED, ItineraryStatus.COMPLETED)) {
            prime(status, 0, 0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.delete("u-1", "it-1"), status.name());

            assertEquals(ErrorCode.CONFLICT, ex.getErrorCode(), status.name());
        }
    }

    @Test
    void ownerMismatchReturnsNotFound() {
        when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete("u-1", "it-1"));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    private void prime(ItineraryStatus status, long active, long completed) {
        Itinerary itinerary = new Itinerary();
        itinerary.setId("it-1");
        itinerary.setUserId("u-1");
        itinerary.setStatus(status);
        itinerary.setHasOrder(true);
        when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.of(itinerary));
        when(orderClient.summaries("u-1", "it-1")).thenReturn(
                new OrderClient.OrderSummaryApiResponse(
                        0, "OK", List.of(new OrderClient.OrderSummaryData("it-1", active, completed))));
    }

}
