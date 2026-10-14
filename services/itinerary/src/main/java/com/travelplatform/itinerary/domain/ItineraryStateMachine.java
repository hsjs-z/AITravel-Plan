package com.travelplatform.itinerary.domain;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;

/**
 * Itinerary lifecycle: DRAFT -> CONFIRMED -> LOCKED -> COMPLETED.
 * Editing a CONFIRMED itinerary (no in-progress order) returns it to DRAFT.
 */
public final class ItineraryStateMachine {

    public void confirm(Itinerary itinerary) {
        require(itinerary.getStatus() == ItineraryStatus.DRAFT, "only draft itineraries can be confirmed");
        itinerary.setStatus(ItineraryStatus.CONFIRMED);
    }

    public void lock(Itinerary itinerary) {
        require(itinerary.getStatus() == ItineraryStatus.CONFIRMED, "only confirmed itineraries can be locked");
        itinerary.setStatus(ItineraryStatus.LOCKED);
        itinerary.setHasOrder(true);
    }

    public void release(Itinerary itinerary) {
        require(itinerary.getStatus() == ItineraryStatus.LOCKED, "only locked itineraries can be released");
        itinerary.setStatus(ItineraryStatus.CONFIRMED);
    }

    public void complete(Itinerary itinerary) {
        require(
                itinerary.getStatus() == ItineraryStatus.LOCKED
                        || itinerary.getStatus() == ItineraryStatus.CONFIRMED,
                "itinerary cannot be completed from its current status");
        itinerary.setStatus(ItineraryStatus.COMPLETED);
    }

    public void edit(Itinerary itinerary) {
        require(
                itinerary.getStatus() == ItineraryStatus.DRAFT
                        || itinerary.getStatus() == ItineraryStatus.CONFIRMED,
                "a locked or completed itinerary cannot be edited");
        itinerary.setStatus(ItineraryStatus.DRAFT);
    }

    public void ensureDeletable(Itinerary itinerary) {
        if (Boolean.TRUE.equals(itinerary.getHasOrder())) {
            throw new BusinessException(ErrorCode.CONFLICT, "itinerary with orders cannot be deleted");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }
}
