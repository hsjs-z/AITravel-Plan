package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryLockRepository extends JpaRepository<InventoryLock, String> {
}
