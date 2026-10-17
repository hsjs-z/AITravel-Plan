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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenicSpotStoreTest {

    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final SpecRepository specRepository = mock(SpecRepository.class);
    private final InventoryUnitRepository unitRepository = mock(InventoryUnitRepository.class);
    private final ScenicSpotStore store = new ScenicSpotStore(
            resourceRepository, specRepository, unitRepository, new ObjectMapper(), 30, 200);

    @Test
    void skipsExistingAttractionByExactNameCityAndType() {
        when(resourceRepository.findByNameAndCityAndType("玉龙雪山", "丽江", ResourceType.ATTRACTION))
                .thenReturn(Optional.of(new Resource()));

        SeedDtos.SeedRecordOutcome outcome =
                store.upsert(attraction("玉龙雪山", "丽江", 100L), null);

        assertEquals(SeedDtos.SeedRecordOutcome.Status.SKIPPED_EXISTING, outcome.status());
        verify(resourceRepository, never()).save(any());
        verify(specRepository, never()).save(any());
        verify(unitRepository, never()).saveAll(any());
    }

    @Test
    void insertsAttractionWithCodeOwnedFieldsAndTicketInventory() {
        when(resourceRepository.findByNameAndCityAndType(any(), any(), any()))
                .thenReturn(Optional.empty());

        SeedDtos.SeedRecordOutcome outcome = store.upsert(
                attraction("玉龙雪山", "丽江", 100L),
                new SeedDtos.SeedCoordinate(new BigDecimal("27.1000000"), new BigDecimal("100.1800000")));

        assertEquals(SeedDtos.SeedRecordOutcome.Status.INSERTED, outcome.status());
        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(resourceCaptor.capture());
        Resource resource = resourceCaptor.getValue();
        assertEquals(ResourceType.ATTRACTION, resource.getType());
        assertEquals(ResourceStatus.ON_SHELF, resource.getStatus());
        assertEquals("云南省", resource.getProvince());
        assertEquals("丽江", resource.getCity());
        assertEquals("5A", resource.getLevel());
        assertEquals("纳西圣山", resource.getHonorTitle());
        assertEquals(100L, resource.getTicketPrice());
        assertEquals(CoordinateStatus.READY, resource.getCoordinateStatus());
        assertTrue(resource.getSeedManaged());
        assertNull(resource.getImageUrl(), "seed must never overwrite a manually curated image");

        ArgumentCaptor<Spec> specCaptor = ArgumentCaptor.forClass(Spec.class);
        verify(specRepository).save(specCaptor.capture());
        assertEquals(SpecType.TICKET_TYPE, specCaptor.getValue().getSpecType());
        assertEquals(100L, specCaptor.getValue().getPrice());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryUnit>> unitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(unitRepository).saveAll(unitsCaptor.capture());
        assertEquals(30, unitsCaptor.getValue().size());
        assertTrue(unitsCaptor.getValue().stream()
                .allMatch(unit -> unit.getAvailable() == 200 && unit.getTotal() == 200));
    }

    @Test
    void freeAttractionKeepsZeroPriceAndCreatesNoTicketModel() {
        when(resourceRepository.findByNameAndCityAndType(any(), any(), any()))
                .thenReturn(Optional.empty());

        SeedDtos.SeedRecordOutcome outcome =
                store.upsert(attraction("洱海", "大理", 0L), null);

        assertEquals(SeedDtos.SeedRecordOutcome.Status.INSERTED, outcome.status());
        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(resourceCaptor.capture());
        assertEquals(0L, resourceCaptor.getValue().getTicketPrice());
        assertEquals(CoordinateStatus.PENDING, resourceCaptor.getValue().getCoordinateStatus());
        assertNull(resourceCaptor.getValue().getLatitude());
        assertNull(resourceCaptor.getValue().getLongitude());
        verify(specRepository, never()).save(any());
        verify(unitRepository, never()).saveAll(any());
    }

    private SeedDtos.ValidatedAttraction attraction(String name, String city, long ticketPrice) {
        return new SeedDtos.ValidatedAttraction(
                "云南省", name, city, "5A", "纳西圣山", "简介",
                new BigDecimal("4.0"), ticketPrice);
    }
}
