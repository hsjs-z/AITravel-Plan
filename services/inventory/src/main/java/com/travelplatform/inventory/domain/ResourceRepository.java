package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, String> {

    List<Resource> findByDestinationAndType(String destination, ResourceType type);

    List<Resource> findByStatus(ResourceStatus status);

    List<Resource> findByStatusAndType(ResourceStatus status, ResourceType type);

    Optional<Resource> findByNameAndCityAndType(String name, String city, ResourceType type);

    long countByTypeAndCoordinateStatus(ResourceType type, CoordinateStatus coordinateStatus);
}
