package com.travelplatform.aiplanning.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-planning-run whitelist. Multiple tool invocations merge into one set and
 * never replace earlier results.
 */
public final class PlanningRunContext {

    private final Map<String, AttractionRef> resources = new LinkedHashMap<>();
    private final Set<String> validSpecPairs = new HashSet<>();
    private final List<String> destinations = new ArrayList<>();

    public synchronized void merge(List<AttractionRef> refs) {
        if (refs == null) {
            return;
        }
        for (AttractionRef ref : refs) {
            if (ref == null || ref.resourceId() == null || ref.resourceId().isBlank()) {
                continue;
            }
            resources.putIfAbsent(ref.resourceId(), ref);
            if (ref.specId() != null && !ref.specId().isBlank()) {
                validSpecPairs.add(ref.resourceId() + "|" + ref.specId());
            }
        }
    }

    public synchronized void recordDestination(String destination) {
        if (destination != null && !destination.isBlank() && !destinations.contains(destination)) {
            destinations.add(destination);
        }
    }

    public synchronized boolean validSpecPair(String resourceId, String specId) {
        return resourceId != null && specId != null
                && validSpecPairs.contains(resourceId + "|" + specId);
    }

    public synchronized boolean validFreeResource(String resourceId) {
        AttractionRef ref = resources.get(resourceId);
        return ref != null && ref.free()
                && (ref.specId() == null || ref.specId().isBlank());
    }

    public synchronized List<String> destinations() {
        return List.copyOf(destinations);
    }
}
