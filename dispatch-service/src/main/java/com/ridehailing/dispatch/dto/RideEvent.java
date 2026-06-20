package com.ridehailing.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private String rideType;
    private String status;
    private Instant requestedAt;
    private Double estimatedFare;
    private String assignedDriverId;
}
