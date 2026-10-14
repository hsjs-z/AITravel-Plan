package com.travelplatform.bookingorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class BookingOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingOrderApplication.class, args);
    }
}
