package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, String> {

    Optional<InventoryUnit> findByResourceIdAndDateAndSpecId(String resourceId, LocalDate date, String specId);

    List<InventoryUnit> findByResourceIdAndDate(String resourceId, LocalDate date);

    List<InventoryUnit> findByResourceIdInAndDate(Collection<String> resourceIds, LocalDate date);
}
