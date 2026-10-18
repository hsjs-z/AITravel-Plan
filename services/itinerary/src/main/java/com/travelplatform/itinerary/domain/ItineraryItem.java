package com.travelplatform.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.math.BigDecimal;

@Entity
@Table(name = "itinerary_item")
public class ItineraryItem {

    @Id
    private String id;

    @Column(nullable = false)
    private String itineraryId;

    @Column(nullable = false)
    private LocalDate dayDate;

    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(name = "suggested_hours")
    private BigDecimal suggestedHours;

    @Column(name = "ticket_price")
    private Long ticketPrice;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String resourceType;

    private String resourceId;

    private String specId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItineraryItemSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", length = 16)
    private ItineraryTimeSlot timeSlot;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getSuggestedHours() {
        return suggestedHours;
    }

    public void setSuggestedHours(BigDecimal suggestedHours) {
        this.suggestedHours = suggestedHours;
    }

    public Long getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Long ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
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

    public ItineraryTimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(ItineraryTimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
