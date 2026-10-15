package com.travelplatform.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.notification.domain.NotificationType;
import com.travelplatform.notification.domain.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationConsumerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationConsumer consumer =
            new NotificationConsumer(notificationService, processedEvents, objectMapper);

    @Test
    void mapsOrderBookedToBookingSucceeded() {
        JsonNode payload = objectMapper.createObjectNode()
                .put("orderId", "o-1")
                .put("userId", "u-1")
                .put("totalAmount", 500)
                .set("items", objectMapper.createArrayNode().add(objectMapper.createObjectNode().put("resourceId", "r-1")));

        Optional<NotificationDraft> draft = consumer.mapToDraft("OrderBooked", payload);

        assertTrue(draft.isPresent());
        assertEquals(NotificationType.BOOKING_SUCCEEDED, draft.get().type());
        assertEquals("u-1", draft.get().userId());
        assertEquals("o-1", draft.get().businessRef());
    }

    @Test
    void mapsOrderPaidAndPaymentSucceededToTheSamePaymentNotification() {
        JsonNode orderPaid = objectMapper.createObjectNode()
                .put("orderId", "o-1").put("userId", "u-1").put("totalAmount", 500);
        JsonNode paymentSucceeded = objectMapper.createObjectNode()
                .put("orderId", "o-1").put("userId", "u-1").put("amount", 500);

        NotificationDraft a = consumer.mapToDraft("OrderPaid", orderPaid).orElseThrow();
        NotificationDraft b = consumer.mapToDraft("PaymentSucceeded", paymentSucceeded).orElseThrow();

        assertEquals(a.type(), b.type());
        assertEquals(a.businessRef(), b.businessRef());
        assertEquals(a.userId(), b.userId());
    }

    @Test
    void skipsEventWithoutUserId() {
        JsonNode payload = objectMapper.createObjectNode().put("orderId", "o-1");

        assertFalse(consumer.mapToDraft("OrderBooked", payload).isPresent());
    }

    @Test
    void processesEachEventIdOnlyOnce() {
        when(processedEvents.existsById("evt-1")).thenReturn(false, true);
        String value = envelope("evt-1", "OrderConfirmed",
                objectMapper.createObjectNode().put("orderId", "o-1").put("userId", "u-1"));
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order.events", 0, 0L, "o-1", value);

        consumer.consume(record);
        consumer.consume(record);

        verify(notificationService, times(1)).record(any(NotificationDraft.class));
    }

    @Test
    void ignoresUnknownEventTypesWithoutRecording() {
        String value = envelope("evt-9", "UnknownEvent",
                objectMapper.createObjectNode().put("userId", "u-1"));

        consumer.consume(new ConsumerRecord<>("order.events", 0, 0L, "o-1", value));

        verify(notificationService, never()).record(any(NotificationDraft.class));
    }

    private String envelope(String id, String type, JsonNode payload) {
        JsonNode root = objectMapper.createObjectNode()
                .put("id", id)
                .put("type", type)
                .put("aggregateId", "agg-1")
                .put("occurredAt", "2026-10-15T10:00:00Z")
                .set("payload", payload);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
