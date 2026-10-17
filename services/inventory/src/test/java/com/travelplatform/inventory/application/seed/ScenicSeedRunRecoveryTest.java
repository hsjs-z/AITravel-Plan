package com.travelplatform.inventory.application.seed;

import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import com.travelplatform.inventory.domain.ScenicSeedRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenicSeedRunRecoveryTest {

    @Test
    void marksLeftoverRunningRunAsFailedOnStartup() {
        ScenicSeedRunRepository repository = mock(ScenicSeedRunRepository.class);
        ScenicSeedRun run = new ScenicSeedRun();
        run.setId("run-stale");
        run.setStatus(ScenicSeedRunStatus.RUNNING);
        run.setStartedAt(Instant.now().minusSeconds(3600));
        when(repository.findByStatus(ScenicSeedRunStatus.RUNNING)).thenReturn(List.of(run));

        new ScenicSeedRunRecovery(repository).run(null);

        assertEquals(ScenicSeedRunStatus.FAILED, run.getStatus());
        assertEquals("interrupted by service restart", run.getFailureReason());
        assertNotNull(run.getFinishedAt());
        verify(repository).save(run);
    }
}
