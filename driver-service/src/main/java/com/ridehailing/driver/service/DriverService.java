package com.ridehailing.driver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.driver.dto.AssignmentEvent;
import com.ridehailing.driver.dto.DriverRegistrationDto;
import com.ridehailing.driver.model.Driver;
import com.ridehailing.driver.model.DriverStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class DriverService {

    private static final String DRIVER_KEY = "driver:";
    private static final String DRIVER_SET_KEY = "drivers:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter fsmTransitions;
    private final Counter autoHealCounter;

    public DriverService(RedisTemplate<String, Object> redisTemplate,
                         KafkaTemplate<String, Object> kafkaTemplate,
                         MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.fsmTransitions = Counter.builder("driver.fsm.transitions.total")
                .description("Driver FSM state transitions").register(meterRegistry);
        this.autoHealCounter = Counter.builder("driver.auto_heal.total")
                .description("Driver auto-heal events").register(meterRegistry);
    }

    // ─── Registration ────────────────────────────────────────────────────────

    public Driver registerDriver(DriverRegistrationDto dto) {
        Driver driver = Driver.builder()
                .driverId(dto.getDriverId())
                .name(dto.getName())
                .vehicleNumber(dto.getVehicleNumber())
                .vehicleType(dto.getVehicleType())
                .lat(dto.getLat())
                .lon(dto.getLon())
                .status(DriverStatus.AVAILABLE)
                .lastUpdated(Instant.now())
                .version(1)
                .rating(5.0)
                .totalTrips(0)
                .build();

        saveDriver(driver);
        redisTemplate.opsForSet().add(DRIVER_SET_KEY, dto.getDriverId());
        log.info("Driver registered: {} | vehicle: {}", dto.getDriverId(), dto.getVehicleType());
        return driver;
    }

    // ─── Location Update ─────────────────────────────────────────────────────

    public void updateLocation(String driverId, double lat, double lon) {
        Driver driver = getDriver(driverId);
        if (driver == null) return;
        driver.setLat(lat);
        driver.setLon(lon);
        driver.setLastUpdated(Instant.now());
        saveDriver(driver);
    }

    // ─── FSM Transition (called by Kafka listener) ───────────────────────────

    public boolean applyAssignment(AssignmentEvent event) {
        Driver driver = getDriver(event.getDriverId());
        if (driver == null) {
            log.warn("Driver not found: {}", event.getDriverId());
            return false;
        }
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            log.warn("Driver {} not available, current status: {}", event.getDriverId(), driver.getStatus());
            return false;
        }
        driver.setStatus(DriverStatus.ASSIGNED);
        driver.setCurrentRideId(event.getRideId());
        driver.setLastUpdated(Instant.now());
        driver.setVersion(driver.getVersion() + 1);
        saveDriver(driver);
        fsmTransitions.increment();
        log.info("Driver {} → ASSIGNED for ride {}", event.getDriverId(), event.getRideId());
        return true;
    }

    public void advanceStatus(String driverId, DriverStatus nextStatus) {
        Driver driver = getDriver(driverId);
        if (driver == null) return;

        DriverStatus current = driver.getStatus();
        boolean valid = switch (nextStatus) {
            case EN_ROUTE_TO_PICKUP -> current == DriverStatus.ASSIGNED;
            case RIDER_PICKED_UP   -> current == DriverStatus.EN_ROUTE_TO_PICKUP;
            case TRIP_COMPLETED    -> current == DriverStatus.RIDER_PICKED_UP;
            case AVAILABLE         -> current == DriverStatus.TRIP_COMPLETED || current == DriverStatus.ASSIGNED;
            default -> false;
        };

        if (!valid) {
            log.warn("Invalid FSM transition {} → {} for driver {}", current, nextStatus, driverId);
            return;
        }

        if (nextStatus == DriverStatus.TRIP_COMPLETED) {
            driver.setTotalTrips(driver.getTotalTrips() + 1);
        }
        if (nextStatus == DriverStatus.AVAILABLE) {
            driver.setCurrentRideId(null);
            // Publish completion event
            kafkaTemplate.send("ride-completed", driverId, Map.of(
                    "rideId", driver.getCurrentRideId() != null ? driver.getCurrentRideId() : "",
                    "driverId", driverId,
                    "completedAt", Instant.now().toString()
            ));
        }

        driver.setStatus(nextStatus);
        driver.setLastUpdated(Instant.now());
        driver.setVersion(driver.getVersion() + 1);
        saveDriver(driver);
        fsmTransitions.increment();
        log.info("Driver {} FSM: {} → {}", driverId, current, nextStatus);
    }

    // ─── Auto-Heal Scheduler ─────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void autoHeal() {
        Set<Object> driverIds = redisTemplate.opsForSet().members(DRIVER_SET_KEY);
        if (driverIds == null) return;

        Instant now = Instant.now();
        for (Object id : driverIds) {
            Driver driver = getDriver(id.toString());
            if (driver == null) continue;

            long minutesStale = Duration.between(driver.getLastUpdated(), now).toMinutes();

            if (driver.getStatus() == DriverStatus.ASSIGNED && minutesStale > 10) {
                log.warn("Auto-heal: resetting stale ASSIGNED driver {}", driver.getDriverId());
                driver.setStatus(DriverStatus.AVAILABLE);
                driver.setCurrentRideId(null);
                driver.setLastUpdated(now);
                saveDriver(driver);
                autoHealCounter.increment();
            } else if (driver.getStatus() == DriverStatus.EN_ROUTE_TO_PICKUP && minutesStale > 30) {
                log.warn("Auto-heal: resetting stale EN_ROUTE driver {}", driver.getDriverId());
                driver.setStatus(DriverStatus.AVAILABLE);
                driver.setCurrentRideId(null);
                driver.setLastUpdated(now);
                saveDriver(driver);
                autoHealCounter.increment();
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    public Driver getDriver(String driverId) {
        Object raw = redisTemplate.opsForValue().get(DRIVER_KEY + driverId);
        if (raw == null) return null;
        try {
            return objectMapper.convertValue(raw, Driver.class);
        } catch (Exception e) {
            log.error("Error deserializing driver {}: {}", driverId, e.getMessage());
            return null;
        }
    }

    public List<Driver> getAvailableDrivers() {
        Set<Object> ids = redisTemplate.opsForSet().members(DRIVER_SET_KEY);
        if (ids == null) return List.of();
        List<Driver> available = new ArrayList<>();
        for (Object id : ids) {
            Driver d = getDriver(id.toString());
            if (d != null && d.getStatus() == DriverStatus.AVAILABLE) {
                available.add(d);
            }
        }
        return available;
    }

    private void saveDriver(Driver driver) {
        redisTemplate.opsForValue().set(DRIVER_KEY + driver.getDriverId(), driver,
                Duration.ofHours(24));
    }
}
