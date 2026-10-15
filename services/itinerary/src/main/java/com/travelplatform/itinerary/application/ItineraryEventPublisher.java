package com.travelplatform.itinerary.application;

import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.itinerary.domain.Itinerary;
import org.springframework.stereotype.Component;

/**
 * Publishes itinerary events through the transactional outbox (T029).
 *
 * <p>{@code ItineraryChanged} is the change notification of GLOSSARY「行程变更通知」:
 * it fires only for an itinerary that already has (or had) an order, so editing a
 * plain draft never spams the user.
 */
@Component
public class ItineraryEventPublisher {

    private static final String AGGREGATE_TYPE = "ITINERARY";

    private final OutboxRecorder recorder;

    public ItineraryEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void itineraryConfirmed(Itinerary itinerary) {
        recorder.record(OutboxTopics.ITINERARY_EVENTS, EventTypes.ITINERARY_CONFIRMED, AGGREGATE_TYPE,
                itinerary.getId(), new ItineraryConfirmed(
                        itinerary.getId(), itinerary.getUserId(), itinerary.getDestination(),
                        itinerary.getStatus().name()));
    }

    public void itineraryChanged(Itinerary itinerary) {
        recorder.record(OutboxTopics.ITINERARY_EVENTS, EventTypes.ITINERARY_CHANGED, AGGREGATE_TYPE,
                itinerary.getId(), new ItineraryChanged(
                        itinerary.getId(), itinerary.getUserId(), itinerary.getDestination(),
                        itinerary.getStatus().name(), Boolean.TRUE.equals(itinerary.getHasOrder())));
    }

    public record ItineraryConfirmed(String itineraryId, String userId, String destination, String status) {
    }

    public record ItineraryChanged(
            String itineraryId, String userId, String destination, String status, boolean hasOrder) {
    }
}
