package com.travelplatform.inventory.application.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.inventory.domain.CoordinateStatus;
import com.travelplatform.inventory.domain.ResourceRepository;
import com.travelplatform.inventory.domain.ResourceType;
import com.travelplatform.inventory.domain.ScenicSeedRun;
import com.travelplatform.inventory.domain.ScenicSeedRunRepository;
import com.travelplatform.inventory.domain.ScenicSeedRunStatus;
import com.travelplatform.inventory.infrastructure.AmapGeocodingClient;
import com.travelplatform.inventory.infrastructure.DeepSeekScenicSpotClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenicSpotSeedServiceTest {

    private final ScenicSeedRunRepository runRepository = mock(ScenicSeedRunRepository.class);
    private final DeepSeekScenicSpotClient llm = mock(DeepSeekScenicSpotClient.class);
    private final AmapGeocodingClient amap = mock(AmapGeocodingClient.class);
    private final ScenicSpotStore store = mock(ScenicSpotStore.class);
    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<ScenicSeedRun> savedRun = new AtomicReference<>();

    private final ScenicSpotSeedService service = new ScenicSpotSeedService(
            runRepository, llm, new ScenicSpotValidator(), amap, store,
            resourceRepository, objectMapper, Runnable::run);

    @Test
    void retriesMappingFailureOnlyOnceAndMarksRunFailed() {
        primeRunRepository();
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);
        when(llm.generate(anyList())).thenThrow(new IllegalStateException("bad json"));
        when(resourceRepository.countByTypeAndCoordinateStatus(
                ResourceType.ATTRACTION, CoordinateStatus.PENDING)).thenReturn(3L);

        service.start(List.of("云南省"), "admin-1");

        verify(llm, times(2)).generate(List.of("云南省"));
        assertEquals(ScenicSeedRunStatus.FAILED, savedRun.get().getStatus());
        String report = savedRun.get().getReportJson();
        assertTrue(report.contains("\"mappingFailed\":true"));
        assertTrue(report.contains("\"attempt\":2"));
        assertTrue(report.contains("\"historicalPendingCoordinates\":3"));
    }

    @Test
    void secondAttemptSuccessIsReportedAndStillCountsOneRetry() {
        primeRunRepository();
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);
        SeedDtos.SeedBatchResponse response = new SeedDtos.SeedBatchResponse(List.of(
                new SeedDtos.ProvinceSeed("云南省", List.of(
                        new SeedDtos.SeedAttraction("玉龙雪山", "丽江", "5A", "纳西圣山",
                                "雪山景区", new BigDecimal("4.0"), 100L)))));
        when(llm.generate(anyList()))
                .thenThrow(new IllegalStateException("bad json"))
                .thenReturn(response);
        when(store.exists("玉龙雪山", "丽江")).thenReturn(false);
        when(amap.geocode("玉龙雪山", "丽江")).thenReturn(Optional.of(
                new SeedDtos.SeedCoordinate(new BigDecimal("27.1"), new BigDecimal("100.18"))));
        when(store.upsert(any(), any())).thenReturn(SeedDtos.SeedRecordOutcome.inserted());
        when(resourceRepository.countByTypeAndCoordinateStatus(
                ResourceType.ATTRACTION, CoordinateStatus.PENDING)).thenReturn(0L);

        service.start(List.of("云南省"), "admin-1");

        verify(llm, times(2)).generate(List.of("云南省"));
        assertEquals(ScenicSeedRunStatus.SUCCEEDED, savedRun.get().getStatus());
        String report = savedRun.get().getReportJson();
        assertTrue(report.contains("\"inserted\":1"));
        assertTrue(report.contains("\"attempt\":2"));
        assertTrue(report.contains("\"coordinateReady\":1"));
    }

    @Test
    void existingRecordIsSkippedWithoutCallingGeocodingOrWriting() {
        primeRunRepository();
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);
        SeedDtos.SeedBatchResponse response = new SeedDtos.SeedBatchResponse(List.of(
                new SeedDtos.ProvinceSeed("云南省", List.of(
                        new SeedDtos.SeedAttraction("玉龙雪山", "丽江", "5A", "纳西圣山",
                                "雪山景区", new BigDecimal("4.0"), 100L)))));
        when(llm.generate(anyList())).thenReturn(response);
        when(store.exists("玉龙雪山", "丽江")).thenReturn(true);
        when(resourceRepository.countByTypeAndCoordinateStatus(
                ResourceType.ATTRACTION, CoordinateStatus.PENDING)).thenReturn(0L);

        service.start(List.of("云南省"), "admin-1");

        verify(amap, times(0)).geocode(any(), any());
        verify(store, times(0)).upsert(any(), any());
        String report = savedRun.get().getReportJson();
        assertTrue(report.contains("\"inserted\":0"));
        assertTrue(report.contains("\"skippedExisting\":1"));
    }

    @Test
    void geocodingFailureStillInsertsWithPendingCoordinate() {
        primeRunRepository();
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);
        SeedDtos.SeedBatchResponse response = new SeedDtos.SeedBatchResponse(List.of(
                new SeedDtos.ProvinceSeed("云南省", List.of(
                        new SeedDtos.SeedAttraction("洱海", "大理", null, null,
                                "高原湖泊", new BigDecimal("3.0"), 0L)))));
        when(llm.generate(anyList())).thenReturn(response);
        when(store.exists("洱海", "大理")).thenReturn(false);
        when(amap.geocode("洱海", "大理")).thenThrow(new IllegalStateException("amap timeout"));
        when(store.upsert(any(), any())).thenReturn(SeedDtos.SeedRecordOutcome.inserted());
        when(resourceRepository.countByTypeAndCoordinateStatus(
                ResourceType.ATTRACTION, CoordinateStatus.PENDING)).thenReturn(1L);

        service.start(List.of("云南省"), "admin-1");

        ArgumentCaptor<SeedDtos.SeedCoordinate> coordinateCaptor =
                ArgumentCaptor.forClass(SeedDtos.SeedCoordinate.class);
        verify(store).upsert(any(), coordinateCaptor.capture());
        assertEquals(null, coordinateCaptor.getValue());
        String report = savedRun.get().getReportJson();
        assertTrue(report.contains("\"coordinatePending\":1"));
        assertTrue(report.contains("geocoding failed"));
    }

    @Test
    void rejectsUnknownProvinceBeforeStartingRun() {
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.start(List.of("火星省"), "admin-1"));

        assertEquals(com.travelplatform.common.api.ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void rejectsStartWhenAnotherRunIsAlreadyRunning() {
        when(llm.isConfigured()).thenReturn(true);
        when(amap.isConfigured()).thenReturn(true);
        when(runRepository.findFirstByStatusOrderByStartedAtDesc(ScenicSeedRunStatus.RUNNING))
                .thenReturn(new ScenicSeedRun());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.start(List.of("云南省"), "admin-1"));

        assertEquals(com.travelplatform.common.api.ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void rejectsStartWhenDeepSeekKeyIsNotConfigured() {
        when(llm.isConfigured()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.start(List.of("云南省"), "admin-1"));

        assertEquals(com.travelplatform.common.api.ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    private void primeRunRepository() {
        when(runRepository.findFirstByStatusOrderByStartedAtDesc(ScenicSeedRunStatus.RUNNING))
                .thenReturn(null);
        when(runRepository.save(any())).thenAnswer(invocation -> {
            ScenicSeedRun run = invocation.getArgument(0);
            savedRun.set(run);
            return run;
        });
        when(runRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedRun.get()));
    }
}
