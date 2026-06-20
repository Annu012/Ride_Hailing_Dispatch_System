package com.ridehailing.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AssignmentEvent {
    private String rideId;
    private String driverId;
    private String riderId;
    private double distanceKm;
    private String driverName;
    private String vehicleNumber;
    private String vehicleType;
    private double driverLat;
    private double driverLon;
    private int version;
}
