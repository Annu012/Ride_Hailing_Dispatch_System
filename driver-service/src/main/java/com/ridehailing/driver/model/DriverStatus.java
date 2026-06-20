package com.ridehailing.driver.model;

/**
 * Driver Finite State Machine
 *
 * AVAILABLE → ASSIGNED → EN_ROUTE_TO_PICKUP → RIDER_PICKED_UP → TRIP_COMPLETED → AVAILABLE
 *
 * Auto-heal rules:
 *   ASSIGNED  > 10 min with no update  → AVAILABLE
 *   EN_ROUTE  > 30 min                 → AVAILABLE
 */
public enum DriverStatus {
    AVAILABLE,
    ASSIGNED,
    EN_ROUTE_TO_PICKUP,
    RIDER_PICKED_UP,
    TRIP_COMPLETED,
    OFFLINE
}
