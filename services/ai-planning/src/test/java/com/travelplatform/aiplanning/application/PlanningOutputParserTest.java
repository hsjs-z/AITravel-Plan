package com.travelplatform.aiplanning.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanningOutputParserTest {

    private final PlanningOutputParser parser = new PlanningOutputParser(new ObjectMapper());

    @Test
    void parsesJsonWithMarkdownFences() {
        PlanningOutput.Plan plan = parser.parse("""
                ```json
                {"destination":"西安","totalDays":1,"days":[{"day":1,"theme":"古都","attractions":[{"name":"大雁塔","suggestedHours":3,"description":"简介","ticketPrice":50,"resourceId":"r-xian-1","specId":"s-xian-1"}]}],"tips":["t1","t2","t3"]}
                ```
                """);

        assertEquals("西安", plan.destination());
        assertEquals(1, plan.days().size());
        assertEquals("大雁塔", plan.days().get(0).attractions().get(0).name());
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalStateException.class, () -> parser.parse("not json"));
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(IllegalStateException.class, () -> parser.parse("  "));
    }
}
