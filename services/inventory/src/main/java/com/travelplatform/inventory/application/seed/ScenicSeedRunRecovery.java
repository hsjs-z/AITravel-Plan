package com.travelplatform.inventory.application.seed;

import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import com.travelplatform.inventory.domain.ScenicSeedRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * A service restart can leave an in-flight seed run in RUNNING forever.
 * Recover it as FAILED so the admin endpoint is usable again.
 */
@Component
public class ScenicSeedRunRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenicSeedRunRecovery.class);

    private final ScenicSeedRunRepository repository;

    public ScenicSeedRunRecovery(ScenicSeedRunRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ScenicSeedRun run : repository.findByStatus(ScenicSeedRunStatus.RUNNING)) {
            run.setStatus(ScenicSeedRunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setFailureReason("interrupted by service restart");
            repository.save(run);
            log.warn("marked interrupted scenic seed run {} as FAILED", run.getId());
        }
    }
}
