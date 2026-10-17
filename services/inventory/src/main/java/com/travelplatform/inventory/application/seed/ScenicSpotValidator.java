package com.travelplatform.inventory.application.seed;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ScenicSpotValidator {

    private static final Set<String> LEVELS = Set.of("5A", "4A", "3A", "2A", "1A");
    private static final int MAX_ATTRACTIONS_PER_PROVINCE = 6;

    public SeedDtos.SeedValidationResult validate(List<String> expectedProvinces,
                                                  SeedDtos.SeedBatchResponse response) {
        Map<String, List<SeedDtos.ValidatedAttraction>> byProvince = new LinkedHashMap<>();
        Map<String, Integer> skippedInvalidByProvince = new LinkedHashMap<>();
        expectedProvinces.forEach(province -> byProvince.put(province, new ArrayList<>()));
        expectedProvinces.forEach(province -> skippedInvalidByProvince.put(province, 0));

        int skippedInvalid = 0;
        List<String> errors = new ArrayList<>();
        if (response == null || response.provinces() == null) {
            return new SeedDtos.SeedValidationResult(byProvince, skippedInvalidByProvince, 0,
                    List.of("AI response does not contain provinces"));
        }

        for (SeedDtos.ProvinceSeed provinceSeed : response.provinces()) {
            if (provinceSeed == null || provinceSeed.province() == null) {
                skippedInvalid++;
                errors.add("province entry without province name");
                continue;
            }
            String province = provinceSeed.province().trim();
            if (!byProvince.containsKey(province)) {
                errors.add("unexpected province in batch: " + province);
                continue;
            }
            List<SeedDtos.SeedAttraction> attractions =
                    provinceSeed.attractions() == null ? List.of() : provinceSeed.attractions();
            Set<String> seenNames = new HashSet<>();
            int acceptedForProvince = 0;
            for (SeedDtos.SeedAttraction attraction : attractions) {
                if (attraction == null) {
                    skippedInvalid++;
                    skippedInvalidByProvince.merge(province, 1, Integer::sum);
                    errors.add(province + ": null attraction");
                    continue;
                }
                if (acceptedForProvince >= MAX_ATTRACTIONS_PER_PROVINCE) {
                    skippedInvalid++;
                    skippedInvalidByProvince.merge(province, 1, Integer::sum);
                    errors.add(province + ": more than 6 attractions, extra dropped");
                    continue;
                }
                String invalid = validateAttraction(attraction);
                if (invalid != null) {
                    skippedInvalid++;
                    skippedInvalidByProvince.merge(province, 1, Integer::sum);
                    errors.add(province + "/" + safe(attraction.name()) + ": " + invalid);
                    continue;
                }
                String name = attraction.name().trim();
                String city = attraction.city().trim();
                if (!seenNames.add(name + "|" + city)) {
                    skippedInvalid++;
                    skippedInvalidByProvince.merge(province, 1, Integer::sum);
                    errors.add(province + "/" + name + ": duplicate name and city in batch");
                    continue;
                }
                byProvince.get(province).add(new SeedDtos.ValidatedAttraction(
                        province,
                        name,
                        city,
                        optional(attraction.level()),
                        optional(attraction.honorTitle()),
                        attraction.summary().trim(),
                        attraction.suggestedHours(),
                        attraction.ticketPrice()));
                acceptedForProvince++;
            }
        }
        return new SeedDtos.SeedValidationResult(byProvince, skippedInvalidByProvince, skippedInvalid, errors);
    }

    private String validateAttraction(SeedDtos.SeedAttraction attraction) {
        if (blank(attraction.name())) {
            return "name is required";
        }
        if (attraction.name().trim().length() > 100) {
            return "name exceeds 100 characters";
        }
        if (blank(attraction.city())) {
            return "city is required";
        }
        if (attraction.city().trim().length() > 64) {
            return "city exceeds 64 characters";
        }
        if (!blank(attraction.level()) && !LEVELS.contains(attraction.level().trim())) {
            return "invalid level: " + attraction.level();
        }
        if (!blank(attraction.honorTitle())
                && attraction.honorTitle().trim().codePointCount(0, attraction.honorTitle().trim().length()) > 10) {
            return "honorTitle exceeds 10 characters";
        }
        if (blank(attraction.summary())) {
            return "summary is required";
        }
        if (attraction.summary().trim().length() > 120) {
            return "summary exceeds 120 characters";
        }
        if (attraction.suggestedHours() == null
                || attraction.suggestedHours().compareTo(new BigDecimal("0.5")) < 0
                || attraction.suggestedHours().compareTo(new BigDecimal("12")) > 0) {
            return "suggestedHours must be between 0.5 and 12";
        }
        if (attraction.ticketPrice() == null || attraction.ticketPrice() < 0
                || attraction.ticketPrice() > 2000) {
            return "ticketPrice must be between 0 and 2000";
        }
        return null;
    }

    private String optional(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "<unnamed>" : value;
    }
}
