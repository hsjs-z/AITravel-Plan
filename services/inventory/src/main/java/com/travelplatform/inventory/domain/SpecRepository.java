package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpecRepository extends JpaRepository<Spec, String> {

    List<Spec> findByResourceId(String resourceId);

    List<Spec> findByResourceIdIn(Collection<String> resourceIds);
}
