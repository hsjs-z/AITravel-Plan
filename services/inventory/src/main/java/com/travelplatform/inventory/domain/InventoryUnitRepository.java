package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, String> {

    Optional<InventoryUnit> findByResourceIdAndDateAndSpecId(String resourceId, LocalDate date, String specId);
}
