package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.itinerary.api.ItineraryController;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryItem;
import com.travelplatform.itinerary.domain.ItineraryItemRepository;
import com.travelplatform.itinerary.domain.ItineraryItemSource;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.domain.ItineraryTimeSlot;
import com.travelplatform.itinerary.infrastructure.InventoryClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItinerarySlotCapacityTest {

    private final ItineraryRepository repository = mock(ItineraryRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final ItineraryEventPublisher events = mock(ItineraryEventPublisher.class);
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final ItineraryService service = new ItineraryService(
            repository, itemRepository, events, new ObjectMapper(), inventoryClient);

    @Test
    void manualAddUsesInventorySnapshotAndAppendsSortOrder() {
        primeDraft();
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.MORNING)).thenReturn(List.of());
        when(inventoryClient.detail("r-1")).thenReturn(paidResource());
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addItem("u-1", "it-1", new ItineraryController.AddItemRequest(
                LocalDate.of(2026, 10, 20), null, null, null,
                ItineraryTimeSlot.MORNING, "r-1", "s-1"));

        ArgumentCaptor<ItineraryItem> captor = ArgumentCaptor.forClass(ItineraryItem.class);
        verify(itemRepository).save(captor.capture());
        ItineraryItem item = captor.getValue();
        assertEquals("测试景区", item.getName());
        assertEquals(100L, item.getTicketPrice());
        assertEquals(new BigDecimal("4.0"), item.getSuggestedHours());
        assertEquals(ItineraryTimeSlot.MORNING, item.getTimeSlot());
        assertEquals(1, item.getSortOrder());
        assertEquals(ItineraryItemSource.MANUAL, item.getSource());
    }

    @Test
    void freeAttractionAllowsEmptySpecId() {
        primeDraft();
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.AFTERNOON)).thenReturn(List.of());
        when(inventoryClient.detail("r-free")).thenReturn(freeResource());
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.addItem("u-1", "it-1", new ItineraryController.AddItemRequest(
                LocalDate.of(2026, 10, 20), null, null, null,
                ItineraryTimeSlot.AFTERNOON, "r-free", null));

        ArgumentCaptor<ItineraryItem> captor = ArgumentCaptor.forClass(ItineraryItem.class);
        verify(itemRepository).save(captor.capture());
        assertNull(captor.getValue().getSpecId());
        assertEquals(0L, captor.getValue().getTicketPrice());
    }

    @Test
    void slotWithThreeItemsRejectsFourth() {
        primeDraft();
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.MORNING)).thenReturn(List.of(
                item("i-1", 1), item("i-2", 2), item("i-3", 3)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addItem(
                "u-1", "it-1", new ItineraryController.AddItemRequest(
                        LocalDate.of(2026, 10, 20), null, null, null,
                        ItineraryTimeSlot.MORNING, "r-1", "s-1")));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void paidAttractionRequiresTicketSpec() {
        primeDraft();
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.MORNING)).thenReturn(List.of());
        when(inventoryClient.detail("r-1")).thenReturn(paidResource());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addItem(
                "u-1", "it-1", new ItineraryController.AddItemRequest(
                        LocalDate.of(2026, 10, 20), null, null, null,
                        ItineraryTimeSlot.MORNING, "r-1", null)));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void nonTicketSpecOrNonAttractionIsRejected() {
        primeDraft();
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.MORNING)).thenReturn(List.of());
        when(inventoryClient.detail("r-1")).thenReturn(paidResource());
        assertThrows(BusinessException.class, () -> service.addItem(
                "u-1", "it-1", new ItineraryController.AddItemRequest(
                        LocalDate.of(2026, 10, 20), null, null, null,
                        ItineraryTimeSlot.MORNING, "r-1", "s-room")));

        when(inventoryClient.detail("r-train")).thenReturn(
                new InventoryClient.ResourceApiResponse(0, "OK",
                        new InventoryClient.ResourceDetail("r-train", "TRADABLE", "动车", "ON_SHELF",
                                100L, new BigDecimal("2"), "交通", null, null, List.of())));
        assertThrows(BusinessException.class, () -> service.addItem(
                "u-1", "it-1", new ItineraryController.AddItemRequest(
                        LocalDate.of(2026, 10, 20), null, null, null,
                        ItineraryTimeSlot.MORNING, "r-train", null)));
    }

    @Test
    void deleteReordersRemainingItemsInSameSlot() {
        primeDraft();
        ItineraryItem deleting = item("i-1", 1);
        when(itemRepository.findById("i-1")).thenReturn(Optional.of(deleting));
        ItineraryItem remaining2 = item("i-2", 2);
        ItineraryItem remaining3 = item("i-3", 3);
        when(itemRepository.findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
                "it-1", ItineraryTimeSlot.MORNING)).thenReturn(List.of(remaining2, remaining3));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteItem("u-1", "it-1", "i-1");

        assertEquals(1, remaining2.getSortOrder());
        assertEquals(2, remaining3.getSortOrder());
        ArgumentCaptor<List<ItineraryItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(itemRepository).saveAll(captor.capture());
        assertEquals(List.of(remaining2, remaining3), captor.getValue());
    }

    @Test
    void ownerMismatchIs404() {
        when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addItem(
                "u-1", "it-1", new ItineraryController.AddItemRequest(
                        LocalDate.of(2026, 10, 20), null, null, null,
                        ItineraryTimeSlot.MORNING, "r-1", "s-1")));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    private void primeDraft() {
        Itinerary itinerary = new Itinerary();
        itinerary.setId("it-1");
        itinerary.setUserId("u-1");
        itinerary.setStatus(ItineraryStatus.DRAFT);
        when(repository.findByIdAndUserId("it-1", "u-1")).thenReturn(Optional.of(itinerary));
    }

    private ItineraryItem item(String id, int order) {
        ItineraryItem item = new ItineraryItem();
        item.setId(id);
        item.setItineraryId("it-1");
        item.setTimeSlot(ItineraryTimeSlot.MORNING);
        item.setSortOrder(order);
        return item;
    }

    private InventoryClient.ResourceApiResponse paidResource() {
        return new InventoryClient.ResourceApiResponse(0, "OK",
                new InventoryClient.ResourceDetail("r-1", "ATTRACTION", "测试景区", "ON_SHELF",
                        100L, new BigDecimal("4.0"), "测试简介",
                        new BigDecimal("34.384"), new BigDecimal("109.278"),
                        List.of(new InventoryClient.SpecView("s-1", "成人票", "TICKET_TYPE", 100L),
                                new InventoryClient.SpecView("s-room", "房型", "ROOM_TYPE", 80L))));
    }

    private InventoryClient.ResourceApiResponse freeResource() {
        return new InventoryClient.ResourceApiResponse(0, "OK",
                new InventoryClient.ResourceDetail("r-free", "ATTRACTION", "免费景区", "ON_SHELF",
                        0L, new BigDecimal("3.0"), "免费简介", null, null, List.of()));
    }
}
