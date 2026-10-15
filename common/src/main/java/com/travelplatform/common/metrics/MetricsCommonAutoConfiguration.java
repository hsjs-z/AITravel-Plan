package com.travelplatform.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Adds the {@code service} common tag to every meter using the application name,
 * so Prometheus queries can group QPS/P99 across the gateway and all services by
 * service without per-module tag configuration.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class MetricsCommonAutoConfiguration {

    @Bean
    public MeterFilter serviceNameCommonTag(
            @Value("${spring.application.name:unknown}") String applicationName) {
        return MeterFilter.commonTags(List.of(Tag.of("service", applicationName)));
    }
}
