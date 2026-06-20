package com.ridehailing.driver.dto;

import com.ridehailing.driver.model.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DriverRegistrationDto {
    private String driverId;
    private String name;
    private String vehicleNumber;
    private String vehicleType;
    private double lat;
    private double lon;
}

