package com.travelplatform.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.observability.TraceContext;
import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.notification.domain.NotificationType;
import com.travelplatform.notification.domain.ProcessedEvent;
import com.travelplatform.notification.domain.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Consumes business events and turns them into notifications (T057).
 *
 * <p>Deduplication is layered: the consumer-side {@code processed_event} table guards
 * against Kafka redelivery (one event id -> one processing), and the notification
 * {@code dedup_key} guards against two different events describing the same business
 * fact (for example OrderPaid and PaymentSucceeded both mean "支付成功" for the same
 * order). The in-app copy is mandatory, the email copy is retried to completion.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEvents;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationService notificationService,
                                ProcessedEventRepository processedEvents,
                                ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.processedEvents = processedEvents;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {OutboxTopics.ITINERARY_EVENTS, OutboxTopics.ORDER_EVENTS, OutboxTopics.PAYMENT_EVENTS},
            groupId = "${spring.kafka.consumer.group-id:notification}")
    public void onEvent(ConsumerRecord<String, String> record) {
        String traceId = header(record, TraceContext.KEY);
        if (traceId != null) {
            TraceContext.put(traceId);
        }
        try {
            consume(record);
        } finally {
            if (traceId != null) {
                TraceContext.clear();
            }
        }
    }

    void consume(ConsumerRecord<String, String> record) {
        JsonNode root;
        try {
            root = objectMapper.readTree(record.value());
        } catch (Exception e) {
            log.warn("dropping unparseable event on {}: {}", record.topic(), e.toString());
            return;
        }
        String eventId = text(root, "id");
        String eventType = text(root, "type");
        if (eventId == null) {
            log.warn("event without id on {} ignored", record.topic());
            return;
        }
        if (processedEvents.existsById(eventId)) {
            return;
        }

        Optional<NotificationDraft> draft = mapToDraft(eventType, root.path("payload"));
        if (draft.isEmpty()) {
            markProcessed(eventId, eventType, record.topic(), "SKIPPED");
            return;
        }
        notificationService.record(draft.get());
        markProcessed(eventId, eventType, record.topic(), "PROCESSED");
    }

    Optional<NotificationDraft> mapToDraft(String eventType, JsonNode payload) {
        if (eventType == null) {
            return Optional.empty();
        }
        String userId = text(payload, "userId");
        if (userId == null) {
            log.warn("event {} without userId skipped for notification", eventType);
            return Optional.empty();
        }

        switch (eventType) {
            case EventTypes.ORDER_BOOKED: {
                String orderId = text(payload, "orderId");
                int itemCount = payload.path("items").size();
                long amount = longValue(payload, "totalAmount");
                return draft(NotificationType.BOOKING_SUCCEEDED, userId, orderId, "预订成功",
                        "订单 " + orderId + " 已成功预订 " + itemCount + " 项资源，合计 ¥" + amount + "。");
            }
            case EventTypes.ORDER_FAILED: {
                String orderId = text(payload, "orderId");
                String reason = text(payload, "failureReason");
                String failed = StreamSupport.stream(payload.path("failedItems").spliterator(), false)
                        .map(item -> text(item, "resourceId"))
                        .filter(java.util.Objects::nonNull)
                        .reduce((a, b) -> a + "、" + b)
                        .orElse("无");
                String body = "订单 " + orderId + " 预订失败"
                        + (reason == null ? "" : "（" + reason + "）")
                        + "。失败资源：" + failed + "；已占用的库存已释放。";
                return draft(NotificationType.BOOKING_FAILED, userId, orderId, "预订失败", body);
            }
            case EventTypes.ORDER_PAID:
            case EventTypes.PAYMENT_SUCCEEDED: {
                String orderId = text(payload, "orderId");
                long amount = EventTypes.ORDER_PAID.equals(eventType)
                        ? longValue(payload, "totalAmount")
                        : longValue(payload, "amount");
                return draft(NotificationType.PAYMENT_SUCCEEDED, userId, orderId, "支付成功",
                        "订单 " + orderId + " 支付 ¥" + amount + " 已成功。");
            }
            case EventTypes.PAYMENT_FAILED: {
                String orderId = text(payload, "orderId");
                String paymentId = text(payload, "paymentId");
                return draft(NotificationType.PAYMENT_FAILED, userId, paymentId, "支付失败",
                        "订单 " + orderId + " 支付失败，请重试。");
            }
            case EventTypes.REFUND_SUCCEEDED: {
                String orderId = text(payload, "orderId");
                long amount = longValue(payload, "amount");
                return draft(NotificationType.REFUND_SUCCEEDED, userId, orderId, "退款成功",
                        "订单 " + orderId + " 已退款 ¥" + amount + "。");
            }
            case EventTypes.ORDER_CONFIRMED: {
                String orderId = text(payload, "orderId");
                return draft(NotificationType.ORDER_CONFIRMED, userId, orderId, "订单已确认",
                        "订单 " + orderId + " 已确认。");
            }
            case EventTypes.ORDER_CANCELLED: {
                String orderId = text(payload, "orderId");
                String refundId = text(payload, "refundId");
                String body = "订单 " + orderId + " 已取消"
                        + (refundId == null ? "，未支付，无退款。" : "，退款已提交。");
                return draft(NotificationType.ORDER_CANCELLED, userId, orderId, "订单已取消", body);
            }
            case EventTypes.ITINERARY_CHANGED: {
                String itineraryId = text(payload, "itineraryId");
                return draft(NotificationType.ITINERARY_CHANGED, userId, itineraryId, "行程变更",
                        "行程 " + itineraryId + " 已更新；已下单的订单内容不会自动变化。");
            }
            default:
                return Optional.empty();
        }
    }

    private Optional<NotificationDraft> draft(NotificationType type, String userId, String businessRef,
                                              String title, String body) {
        return Optional.of(new NotificationDraft(type, userId, businessRef, title, body));
    }

    private void markProcessed(String eventId, String eventType, String topic, String outcome) {
        ProcessedEvent marker = new ProcessedEvent();
        marker.setEventId(eventId);
        marker.setEventType(eventType == null ? "UNKNOWN" : eventType);
        marker.setTopic(topic);
        marker.setOutcome(outcome);
        marker.setProcessedAt(Instant.now());
        processedEvents.save(marker);
    }

    private String header(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? 0L : value.asLong();
    }
}
