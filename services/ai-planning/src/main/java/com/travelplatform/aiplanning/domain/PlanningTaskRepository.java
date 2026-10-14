package com.travelplatform.aiplanning.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanningTaskRepository extends JpaRepository<PlanningTask, String> {

    Optional<PlanningTask> findByIdAndUserId(String id, String userId);
}
