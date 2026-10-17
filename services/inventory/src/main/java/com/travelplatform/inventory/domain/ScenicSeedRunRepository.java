package com.travelplatform.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenicSeedRunRepository extends JpaRepository<ScenicSeedRun, String> {

    List<ScenicSeedRun> findByStatus(ScenicSeedRunStatus status);

    ScenicSeedRun findFirstByStatusOrderByStartedAtDesc(ScenicSeedRunStatus status);
}
