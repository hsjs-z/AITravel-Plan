package com.travelplatform.itinerary.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, String> {

    List<ItineraryItem> findByItineraryId(String itineraryId);

    void deleteByItineraryId(String itineraryId);
}
