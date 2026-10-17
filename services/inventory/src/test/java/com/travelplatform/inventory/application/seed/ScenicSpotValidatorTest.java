package com.travelplatform.inventory.application.seed;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenicSpotValidatorTest {

    private final ScenicSpotValidator validator = new ScenicSpotValidator();

    @Test
    void acceptsMissingLevelAndHonorTitleAsNull() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction("玉龙雪山", "丽江", null, null, "雪山景区",
                        new BigDecimal("4.0"), 100L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(0, result.skippedInvalid());
        SeedDtos.ValidatedAttraction attraction =
                result.byProvince().get("云南省").get(0);
        assertNull(attraction.level(), "unknown level must stay null instead of being invented");
        assertNull(attraction.honorTitle(), "unknown honor title must stay null instead of being invented");
    }

    @Test
    void acceptsBlankLevelAndHonorTitleAsNull() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction("洱海", "大理", " ", " ", "高原湖泊",
                        new BigDecimal("3.0"), 0L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(0, result.skippedInvalid());
        SeedDtos.ValidatedAttraction attraction =
                result.byProvince().get("云南省").get(0);
        assertNull(attraction.level());
        assertNull(attraction.honorTitle());
        assertEquals(0L, attraction.ticketPrice(), "free attractions keep an explicit zero price");
    }

    @Test
    void rejectsInvalidLevelButKeepsOtherRecords() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction("A", "昆明", "6A", "称号", "简介",
                        new BigDecimal("2.0"), 10L),
                attraction("B", "昆明", "4A", "称号", "简介",
                        new BigDecimal("2.0"), 10L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(1, result.skippedInvalid());
        assertEquals(1, result.byProvince().get("云南省").size());
        assertEquals("B", result.byProvince().get("云南省").get(0).name());
    }

    @Test
    void rejectsHonorTitleLongerThanTenCharacters() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction("A", "昆明", "5A", "一二三四五六七八九十十一", "简介",
                        new BigDecimal("2.0"), 10L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(1, result.skippedInvalid());
        assertTrue(result.errors().get(0).contains("honorTitle exceeds 10 characters"));
    }

    @Test
    void rejectsRecordsWithMissingRequiredFields() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction(" ", "昆明", "5A", null, "简介", new BigDecimal("2.0"), 10L),
                attraction("B", null, "5A", null, "简介", new BigDecimal("2.0"), 10L),
                attraction("C", "昆明", "5A", null, " ", new BigDecimal("2.0"), 10L),
                attraction("D", "昆明", "5A", null, "简介", null, 10L),
                attraction("E", "昆明", "5A", null, "简介", new BigDecimal("2.0"), null)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(5, result.skippedInvalid());
        assertTrue(result.byProvince().get("云南省").isEmpty());
    }

    @Test
    void capsEachProvinceAtSixAttractions() {
        List<SeedDtos.SeedAttraction> attractions = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(index -> attraction("景区" + index, "昆明", "5A", null, "简介",
                        new BigDecimal("2.0"), 10L))
                .toList();

        SeedDtos.SeedValidationResult result =
                validator.validate(List.of("云南省"), response("云南省", attractions));

        assertEquals(6, result.byProvince().get("云南省").size());
        assertEquals(1, result.skippedInvalid());
    }

    @Test
    void rejectsDuplicateNameAndCityWithinTheSameProvince() {
        SeedDtos.SeedBatchResponse response = response("云南省", List.of(
                attraction("玉龙雪山", "丽江", "5A", null, "简介1",
                        new BigDecimal("4.0"), 100L),
                attraction("玉龙雪山", "丽江", "5A", null, "简介2",
                        new BigDecimal("4.0"), 100L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertEquals(1, result.byProvince().get("云南省").size());
        assertEquals(1, result.skippedInvalid());
    }

    @Test
    void rejectsUnexpectedProvinceWithoutCountingItIntoExpectedProvince() {
        SeedDtos.SeedBatchResponse response = response("北京市", List.of(
                attraction("故宫", "北京", "5A", null, "简介",
                        new BigDecimal("4.0"), 60L)));

        SeedDtos.SeedValidationResult result = validator.validate(List.of("云南省"), response);

        assertTrue(result.byProvince().get("云南省").isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("unexpected province")));
    }

    private SeedDtos.SeedBatchResponse response(String province, List<SeedDtos.SeedAttraction> attractions) {
        return new SeedDtos.SeedBatchResponse(
                List.of(new SeedDtos.ProvinceSeed(province, attractions)));
    }

    private SeedDtos.SeedAttraction attraction(String name,
                                               String city,
                                               String level,
                                               String honorTitle,
                                               String summary,
                                               BigDecimal suggestedHours,
                                               Long ticketPrice) {
        return new SeedDtos.SeedAttraction(
                name, city, level, honorTitle, summary, suggestedHours, ticketPrice);
    }
}
