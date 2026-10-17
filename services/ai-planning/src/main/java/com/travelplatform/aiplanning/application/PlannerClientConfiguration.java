package com.travelplatform.aiplanning.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DeepSeek HTTP client with an explicit single-request timeout, and a dedicated
 * executor so the whole generate call can be bounded separately.
 */
@Configuration
public class PlannerClientConfiguration {

    @Bean
    public RestClient.Builder deepSeekRestClientBuilder(
            @Value("${planning.deepseek-timeout-ms:30000}") long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1000, timeoutMs)));
        return RestClient.builder().requestFactory(factory);
    }

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("app-task-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "plannerCallExecutor")
    public ThreadPoolTaskExecutor plannerCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("planner-call-");
        executor.initialize();
        return executor;
    }
}
