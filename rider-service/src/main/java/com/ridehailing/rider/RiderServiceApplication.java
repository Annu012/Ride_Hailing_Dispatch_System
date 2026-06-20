package com.ridehailing.rider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiderServiceApplication.class, args);
    }
}
