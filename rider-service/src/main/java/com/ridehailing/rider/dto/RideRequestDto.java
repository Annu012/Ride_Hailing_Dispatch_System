package com.ridehailing.rider.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RideRequestDto {

    @NotBlank(message = "Rider ID is required")
    private String riderId;

    @NotBlank(message = "Rider name is required")
    private String riderName;

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double pickupLat;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double pickupLon;

    @NotNull
    @DecimalMin("-90.0") @DecimalMax("90.0")
    private Double dropLat;

    @NotNull
    @DecimalMin("-180.0") @DecimalMax("180.0")
    private Double dropLon;

    @NotBlank
    private String pickupAddress;

    @NotBlank
    private String dropAddress;

    // ECONOMY, PREMIUM, XL
    @NotNull
    private RideType rideType;
}
