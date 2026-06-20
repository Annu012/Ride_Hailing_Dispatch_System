package com.ridehailing.rider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideEvent {
    private String rideId;
    private String riderId;
    private String riderName;
    private Double pickupLat;
    private Double pickupLon;
    private Double dropLat;
    private Double dropLon;
    private String pickupAddress;
    private String dropAddress;
    private RideType rideType;
    private String status;      // REQUESTED, ASSIGNED, PICKED_UP, COMPLETED, CANCELLED
    private Instant requestedAt;
    private String assignedDriverId;
    private Double estimatedFare;
}
