package com.travelplatform.inventory.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class ResourceAttractionMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void uniqueKeyAndAttractionColumnsAreApplied() {
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'resource'
                  AND index_name = 'uk_resource_name_city_type'
                """, Integer.class);
        assertEquals(1, indexCount);

        Integer duplicates = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT name, city, type FROM resource
                  GROUP BY name, city, type HAVING COUNT(*) > 1
                ) duplicate_groups
                """, Integer.class);
        assertEquals(0, duplicates);
    }

    @Test
    void legacyYunnanRowIsBackfilledWithoutChangingItsType() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT type, province, city FROM resource WHERE id = 'r-attraction-1'
                """);
        assumeTrue(!rows.isEmpty(), "legacy v1 seed is not present in this database");

        Map<String, Object> row = rows.get(0);
        assertEquals("TRADABLE", row.get("type"));
        assertEquals("云南", row.get("province"));
        assertEquals("丽江", row.get("city"));
    }

    @Test
    void loadTestFixtureStaysOutOfDestinationBackfill() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT province, city FROM resource WHERE id = 'load-test-resource'
                """);
        assumeTrue(!rows.isEmpty(), "load-test fixture is not present in this database");

        assertNull(rows.get(0).get("province"));
        assertNull(rows.get(0).get("city"));
    }
}
