package com.ridehailing.driver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Driver {
    private String driverId;
    private String name;
    private String vehicleNumber;
    private String vehicleType;   // ECONOMY, PREMIUM, XL
    private double lat;
    private double lon;
    private DriverStatus status;
    private String currentRideId;
    private Instant lastUpdated;
    private int version;
    private double rating;
    private int totalTrips;
}
