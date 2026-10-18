package com.travelplatform.itinerary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.itinerary.domain.Itinerary;
import com.travelplatform.itinerary.domain.ItineraryRepository;
import com.travelplatform.itinerary.domain.ItineraryStatus;
import com.travelplatform.itinerary.infrastructure.AiPlanningClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Generates itinerary tips after confirmation, off the request thread. Failures
 * deliberately leave tips empty and never affect the CONFIRMED state.
 */
@Component
public class ItineraryTipsService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryTipsService.class);

    private final ItineraryRepository repository;
    private final AiPlanningClient aiPlanningClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;

    public ItineraryTipsService(ItineraryRepository repository,
                                AiPlanningClient aiPlanningClient,
                                ObjectMapper objectMapper,
                                @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.repository = repository;
        this.aiPlanningClient = aiPlanningClient;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public void generateAsync(String itineraryId, String destination, int days,
                              List<String> attractions) {
        executor.execute(() -> generate(itineraryId, destination, days, attractions));
    }

    void generate(String itineraryId, String destination, int days, List<String> attractions) {
        try {
            AiPlanningClient.TipsApiResponse response = aiPlanningClient.generateTips(
                    new AiPlanningClient.TipsRequest(destination, days, attractions));
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return;
            }
            Itinerary itinerary = repository.findById(itineraryId).orElse(null);
            if (itinerary == null || itinerary.getStatus() != ItineraryStatus.CONFIRMED) {
                return;
            }
            itinerary.setTips(objectMapper.writeValueAsString(response.data()));
            itinerary.setUpdatedAt(Instant.now());
            repository.save(itinerary);
        } catch (Exception e) {
            log.warn("failed to generate tips for itinerary {}: {}", itineraryId, e.toString());
        }
    }
}
