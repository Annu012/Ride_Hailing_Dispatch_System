package com.ridehailing.dispatch.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridehailing.dispatch.dto.AssignmentEvent;
import com.ridehailing.dispatch.dto.DriverSnapshot;
import com.ridehailing.dispatch.dto.RideEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DispatchService {

    private static final String RIDE_QUEUE_KEY     = "dispatch:ride-queue";
    private static final String DRIVER_LOCK_PREFIX = "dispatch:lock:driver:";
    private static final String DRIVER_KEY_PREFIX  = "driver:";
    private static final String DRIVER_SET_KEY     = "drivers:all";
    private static final String TOPIC_ASSIGNED     = "ride-assigned";

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final Counter assignmentsPublished;
    private final Counter noDriverAvailable;
    private final Counter lockFailures;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    public DispatchService(RedisTemplate<String, Object> redisTemplate,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.assignmentsPublished = Counter.builder("dispatch.assignments.published.total")
                .register(meterRegistry);
        this.noDriverAvailable = Counter.builder("dispatch.no_driver_available.total")
                .register(meterRegistry);
        this.lockFailures = Counter.builder("dispatch.lock.failures.total")
                .register(meterRegistry);

        Gauge.builder("dispatch.ride.queue.depth", queueDepth, AtomicInteger::get)
                .description("Current ride queue depth")
                .register(meterRegistry);
    }

    public void enqueueRide(RideEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            double score = System.currentTimeMillis() / 1000.0;
            redisTemplate.opsForZSet().add(RIDE_QUEUE_KEY, json, score);
            queueDepth.incrementAndGet();
            log.info("Enqueued ride: {} | type: {}", event.getRideId(), event.getRideType());
        } catch (Exception e) {
            log.error("Failed to enqueue ride {}: {}", event.getRideId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatchLoop() {
        Set<Object> top = redisTemplate.opsForZSet().range(RIDE_QUEUE_KEY, 0, 0);
        if (top == null || top.isEmpty()) return;

        String json = top.iterator().next().toString();
        RideEvent ride;
        try {
            ride = objectMapper.readValue(json, RideEvent.class);
        } catch (Exception e) {
            log.error("Corrupt ride in queue, removing: {}", e.getMessage());
            redisTemplate.opsForZSet().remove(RIDE_QUEUE_KEY, json);
            queueDepth.decrementAndGet();
            return;
        }

        List<DriverSnapshot> available = getAvailableDrivers(ride.getRideType());
        if (available.isEmpty()) {
            noDriverAvailable.increment();
            log.warn("No drivers available for ride {} (type: {})", ride.getRideId(), ride.getRideType());
            return;
        }

        DriverSnapshot nearest = findNearest(available, ride.getPickupLat(), ride.getPickupLon());
        if (nearest == null) return;

        String lockKey = DRIVER_LOCK_PREFIX + nearest.getDriverId();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, ride.getRideId(), Duration.ofSeconds(10));

        if (!Boolean.TRUE.equals(locked)) {
            lockFailures.increment();
            log.warn("Could not lock driver {}, will retry", nearest.getDriverId());
            return;
        }

        try {
            double distKm = haversineKm(
                    ride.getPickupLat(), ride.getPickupLon(),
                    nearest.getLat(), nearest.getLon());

            AssignmentEvent assignment = AssignmentEvent.builder()
                    .rideId(ride.getRideId())
                    .driverId(nearest.getDriverId())
                    .riderId(ride.getRiderId())
                    .distanceKm(distKm)
                    .driverName(nearest.getName())
                    .vehicleNumber(nearest.getVehicleNumber())
                    .vehicleType(nearest.getVehicleType())
                    .driverLat(nearest.getLat())
                    .driverLon(nearest.getLon())
                    .version(nearest.getVersion())
                    .build();

            // ✅ FIX: Pass the object directly — do NOT call objectMapper.writeValueAsString()
            // here. KafkaTemplate<String, Object> uses JsonSerializer which serializes the
            // object itself. Manually converting to String first causes double-encoding:
            // the String gets serialized again, arriving at consumers as "\"{ ... }\"".
            kafkaTemplate.send(TOPIC_ASSIGNED, ride.getRideId(), assignment);

            redisTemplate.opsForZSet().remove(RIDE_QUEUE_KEY, json);
            queueDepth.decrementAndGet();
            assignmentsPublished.increment();

            log.info("✅ Dispatched ride {} → driver {} ({} km away)",
                    ride.getRideId(), nearest.getDriverId(),
                    String.format("%.2f", distKm));

        } catch (Exception e) {
            log.error("Failed to publish assignment for ride {}: {}", ride.getRideId(), e.getMessage());
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private List<DriverSnapshot> getAvailableDrivers(String rideType) {
        Set<Object> ids = redisTemplate.opsForSet().members(DRIVER_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            log.warn("No driver IDs found in Redis set '{}'", DRIVER_SET_KEY);
            return List.of();
        }

        List<DriverSnapshot> result = new ArrayList<>();
        for (Object id : ids) {
            try {
                String driverId = id.toString().replaceAll("^\"|\"$", "");

                Object raw = redisTemplate.opsForValue().get(DRIVER_KEY_PREFIX + driverId);
                if (raw == null) continue;

                String rawStr = raw.toString();

                JsonNode node = objectMapper.readTree(rawStr);
                JsonNode driverNode;
                if (node.isArray() && node.size() == 2) {
                    driverNode = node.get(1);
                } else {
                    driverNode = node;
                }

                DriverSnapshot d = objectMapper.treeToValue(driverNode, DriverSnapshot.class);

                if ("AVAILABLE".equals(d.getStatus()) &&
                        (rideType == null || rideType.equalsIgnoreCase(d.getVehicleType()))) {
                    result.add(d);
                    log.info("Driver {} is AVAILABLE (type: {})", d.getDriverId(), d.getVehicleType());
                }
            } catch (Exception e) {
                log.warn("Could not deserialize driver {}: {}", id, e.getMessage());
            }
        }
        log.info("Found {} available drivers for type {}", result.size(), rideType);
        return result;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private DriverSnapshot findNearest(List<DriverSnapshot> drivers, double lat, double lon) {
        return drivers.stream()
                .min(Comparator.comparingDouble(d -> haversineKm(lat, lon, d.getLat(), d.getLon())))
                .orElse(null);
    }

    public int getQueueDepth() {
        Long size = redisTemplate.opsForZSet().size(RIDE_QUEUE_KEY);
        return size != null ? size.intValue() : 0;
    }
}
