package com.ridehailing.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DriverSnapshot {
    private String driverId;
    private String name;
    private String vehicleNumber;
    private String vehicleType;
    private double lat;
    private double lon;
    private String status;
    private int version;
}
