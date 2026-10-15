package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, String> {

    List<Resource> findByDestinationAndType(String destination, ResourceType type);

    List<Resource> findByStatus(ResourceStatus status);

    List<Resource> findByStatusAndType(ResourceStatus status, ResourceType type);
}
