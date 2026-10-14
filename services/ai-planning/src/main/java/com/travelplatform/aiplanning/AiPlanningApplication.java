package com.travelplatform.aiplanning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiPlanningApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiPlanningApplication.class, args);
    }
}
