package com.travelplatform.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "itinerary_item")
public class ItineraryItem {

    @Id
    private String id;

    @Column(nullable = false)
    private String itineraryId;

    @Column(nullable = false)
    private LocalDate dayDate;

    @Column(nullable = false, length = 1024)
    private String description;

    private String resourceType;

    private String resourceId;

    private String specId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItineraryItemSource source;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItineraryId() {
        return itineraryId;
    }

    public void setItineraryId(String itineraryId) {
        this.itineraryId = itineraryId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public void setDayDate(LocalDate dayDate) {
        this.dayDate = dayDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getSpecId() {
        return specId;
    }

    public void setSpecId(String specId) {
        this.specId = specId;
    }

    public ItineraryItemSource getSource() {
        return source;
    }

    public void setSource(ItineraryItemSource source) {
        this.source = source;
    }
}
