package com.travelplatform.itinerary.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, String> {

    List<ItineraryItem> findByItineraryId(String itineraryId);

    List<ItineraryItem> findByItineraryIdOrderByDayDateAscTimeSlotAscSortOrderAsc(String itineraryId);

    List<ItineraryItem> findByItineraryIdAndTimeSlotOrderBySortOrderAsc(
            String itineraryId, ItineraryTimeSlot timeSlot);

    long countByItineraryIdAndTimeSlot(String itineraryId, ItineraryTimeSlot timeSlot);

    void deleteByItineraryId(String itineraryId);
}
