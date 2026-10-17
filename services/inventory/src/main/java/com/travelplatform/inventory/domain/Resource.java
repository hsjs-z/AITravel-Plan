package com.travelplatform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "resource")
public class Resource {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceStatus status;

    @Column(columnDefinition = "TEXT")
    private String attributes;

    private String province;

    private String city;

    @Column(name = "level")
    private String level;

    @Column(name = "honor_title")
    private String honorTitle;

    private String summary;

    @Column(name = "suggested_hours")
    private BigDecimal suggestedHours;

    @Column(name = "ticket_price", nullable = false)
    private Long ticketPrice = 0L;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "coordinate_status", nullable = false, length = 16)
    private CoordinateStatus coordinateStatus = CoordinateStatus.PENDING;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "seed_managed", nullable = false)
    private Boolean seedManaged = false;

    @Column(name = "seeded_at")
    private Instant seededAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ResourceType getType() {
        return type;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void setStatus(ResourceStatus status) {
        this.status = status;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getHonorTitle() {
        return honorTitle;
    }

    public void setHonorTitle(String honorTitle) {
        this.honorTitle = honorTitle;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public CoordinateStatus getCoordinateStatus() {
        return coordinateStatus;
    }

    public void setCoordinateStatus(CoordinateStatus coordinateStatus) {
        this.coordinateStatus = coordinateStatus;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getSeedManaged() {
        return seedManaged;
    }

    public void setSeedManaged(Boolean seedManaged) {
        this.seedManaged = seedManaged;
    }

    public Instant getSeededAt() {
        return seededAt;
    }

    public void setSeededAt(Instant seededAt) {
        this.seededAt = seededAt;
    }
}
