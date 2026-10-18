package com.travelplatform.itinerary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ItineraryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ItineraryApplication.class, args);
    }
}
