package com.travelplatform.itinerary.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItineraryRepository extends JpaRepository<Itinerary, String> {

    Optional<Itinerary> findByIdAndUserId(String id, String userId);
}
