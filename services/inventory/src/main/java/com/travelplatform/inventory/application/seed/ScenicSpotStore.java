package com.travelplatform.inventory.application.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.inventory.domain.CoordinateStatus;
import com.travelplatform.inventory.domain.InventoryUnit;
import com.travelplatform.inventory.domain.InventoryUnitRepository;
import com.travelplatform.inventory.domain.Resource;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceStatus;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.Spec;
import com.travelplatform.inventory.domain.SpecRepository;
import com.travelplatform.inventory.domain.SpecType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ScenicSpotStore {

    private final ResourceRepository resourceRepository;
    private final SpecRepository specRepository;
    private final InventoryUnitRepository unitRepository;
    private final ObjectMapper objectMapper;
    private final int inventoryHorizonDays;
    private final int dailyCapacity;

    public ScenicSpotStore(ResourceRepository resourceRepository,
                           SpecRepository specRepository,
                           InventoryUnitRepository unitRepository,
                           ObjectMapper objectMapper,
                           @Value("${seed.inventory-horizon-days:365}") int inventoryHorizonDays,
                           @Value("${seed.daily-capacity:200}") int dailyCapacity) {
        this.resourceRepository = resourceRepository;
        this.specRepository = specRepository;
        this.unitRepository = unitRepository;
        this.objectMapper = objectMapper;
        this.inventoryHorizonDays = Math.max(1, inventoryHorizonDays);
        this.dailyCapacity = Math.max(1, dailyCapacity);
    }

    public boolean exists(String name, String city) {
        return resourceRepository.findByNameAndCityAndType(name, city, ResourceType.ATTRACTION).isPresent();
    }

    @Transactional
    public SeedDtos.SeedRecordOutcome upsert(SeedDtos.ValidatedAttraction attraction,
                                             SeedDtos.SeedCoordinate coordinate) {
        if (exists(attraction.name(), attraction.city())) {
            return SeedDtos.SeedRecordOutcome.skipped();
        }

        Resource resource = new Resource();
        resource.setId("r-attraction-" + UUID.randomUUID());
        resource.setType(ResourceType.ATTRACTION);
        resource.setName(attraction.name());
        resource.setDestination(attraction.province());
        resource.setProvince(attraction.province());
        resource.setCity(attraction.city());
        resource.setLevel(attraction.level());
        resource.setHonorTitle(attraction.honorTitle());
        resource.setSummary(attraction.summary());
        resource.setSuggestedHours(attraction.suggestedHours());
        resource.setTicketPrice(attraction.ticketPrice());
        resource.setStatus(ResourceStatus.ON_SHELF);
        resource.setSeedManaged(true);
        resource.setSeededAt(Instant.now());
        resource.setImageUrl(null);
        if (coordinate == null) {
            resource.setCoordinateStatus(CoordinateStatus.PENDING);
        } else {
            resource.setLatitude(coordinate.latitude());
            resource.setLongitude(coordinate.longitude());
            resource.setCoordinateStatus(CoordinateStatus.READY);
        }
        try {
            resource.setAttributes(objectMapper.writeValueAsString(Map.of(
                    "category", "ATTRACTION",
                    "description", attraction.summary())));
        } catch (Exception e) {
            return SeedDtos.SeedRecordOutcome.failed("failed to serialize resource attributes: " + e.getMessage());
        }
        resourceRepository.save(resource);

        if (attraction.ticketPrice() > 0) {
            createTicketSpecAndInventory(resource, attraction.ticketPrice());
        }
        return SeedDtos.SeedRecordOutcome.inserted();
    }

    private void createTicketSpecAndInventory(Resource resource, long ticketPrice) {
        Spec spec = new Spec();
        spec.setId("s-ticket-" + UUID.randomUUID());
        spec.setResourceId(resource.getId());
        spec.setSpecType(SpecType.TICKET_TYPE);
        spec.setName("成人票");
        spec.setPrice(ticketPrice);
        specRepository.save(spec);

        List<InventoryUnit> units = new ArrayList<>();
        LocalDate start = LocalDate.now();
        for (int i = 0; i < inventoryHorizonDays; i++) {
            LocalDate date = start.plusDays(i);
            InventoryUnit unit = new InventoryUnit();
            unit.setId("u-" + resource.getId() + "-" + spec.getId() + "-" + date);
            unit.setResourceId(resource.getId());
            unit.setSpecId(spec.getId());
            unit.setDate(date);
            unit.setTotal(dailyCapacity);
            unit.setAvailable(dailyCapacity);
            unit.setLocked(0);
            unit.setVersion(0L);
            units.add(unit);
        }
        unitRepository.saveAll(units);
    }
}
