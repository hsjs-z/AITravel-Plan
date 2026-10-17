package com.travelplatform.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.GlobalExceptionHandler;
import com.travelplatform.inventory.application.seed.ScenicSpotSeedService;
import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import com.travelplatform.inventory.domain.ScenicSeedRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSeedControllerTest {

    private final ScenicSpotSeedService seedService = mock(ScenicSpotSeedService.class);
    private final ScenicSeedRunRepository runRepository = mock(ScenicSeedRunRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminSeedController(seedService, runRepository, objectMapper))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void missingAdminRoleIsHiddenAs404AndNeverStartsRun() throws Exception {
        mockMvc.perform(post("/admin/seed/scenic-spots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provinces\":[\"云南省\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(post("/admin/seed/scenic-spots")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provinces\":[\"云南省\"]}"))
                .andExpect(status().isNotFound());

        verify(seedService, never()).start(any(), any());
    }

    @Test
    void adminStartsAsynchronousRunWith202AndRunId() throws Exception {
        when(seedService.start(eq(java.util.List.of("云南省")), eq("admin-1")))
                .thenReturn("run-1");

        mockMvc.perform(post("/admin/seed/scenic-spots")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provinces\":[\"云南省\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void adminReadsStoredReport() throws Exception {
        ScenicSeedRun run = new ScenicSeedRun();
        run.setId("run-1");
        run.setStatus(ScenicSeedRunStatus.SUCCEEDED);
        run.setRequestedBy("admin-1");
        run.setStartedAt(Instant.parse("2026-10-17T12:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-10-17T12:05:00Z"));
        run.setReportJson("{\"summary\":{\"inserted\":6}}");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/admin/seed/scenic-spots/run-1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.report.summary.inserted").value(6));
    }

    @Test
    void unknownRunIs404() throws Exception {
        when(runRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/seed/scenic-spots/missing")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }
}
