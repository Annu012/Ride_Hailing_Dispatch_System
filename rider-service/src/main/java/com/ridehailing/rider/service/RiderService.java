package com.ridehailing.rider.service;

import com.ridehailing.rider.dto.RideEvent;
import com.ridehailing.rider.dto.RideRequestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class RiderService {

    private static final String TOPIC_RIDE_REQUESTED = "ride-requested";
    private static final String RIDE_KEY_PREFIX = "ride:";
    private static final double BASE_FARE = 30.0;
    private static final double PER_KM_RATE = 12.0;

    private final KafkaTemplate<String, RideEvent> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Counter rideRequestCounter;

    public RiderService(KafkaTemplate<String, RideEvent> kafkaTemplate,
                        RedisTemplate<String, Object> redisTemplate,
                        MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.rideRequestCounter = Counter.builder("ride.requests.total")
                .description("Total ride requests received")
                .register(meterRegistry);
    }

    public RideEvent requestRide(RideRequestDto dto) {
        String rideId = "RIDE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        double estimatedFare = calculateFare(dto);

        RideEvent event = RideEvent.builder()
                .rideId(rideId)
                .riderId(dto.getRiderId())
                .riderName(dto.getRiderName())
                .pickupLat(dto.getPickupLat())
                .pickupLon(dto.getPickupLon())
                .dropLat(dto.getDropLat())
                .dropLon(dto.getDropLon())
                .pickupAddress(dto.getPickupAddress())
                .dropAddress(dto.getDropAddress())
                .rideType(dto.getRideType())
                .status("REQUESTED")
                .requestedAt(Instant.now())
                .estimatedFare(estimatedFare)
                .build();

        // Store in Redis with 2h TTL
        redisTemplate.opsForHash().putAll(RIDE_KEY_PREFIX + rideId, Map.of(
                "rideId", rideId,
                "riderId", dto.getRiderId(),
                "status", "REQUESTED",
                "pickupLat", String.valueOf(dto.getPickupLat()),
                "pickupLon", String.valueOf(dto.getPickupLon()),
                "rideType", dto.getRideType().name(),
                "estimatedFare", String.valueOf(estimatedFare),
                "requestedAt", Instant.now().toString()
        ));
        redisTemplate.expire(RIDE_KEY_PREFIX + rideId, Duration.ofHours(2));

        // Publish to Kafka
        kafkaTemplate.send(TOPIC_RIDE_REQUESTED, rideId, event);
        rideRequestCounter.increment();

        log.info("Ride requested: {} by rider: {} | type: {} | fare: ₹{}",
                rideId, dto.getRiderId(), dto.getRideType(), estimatedFare);
        return event;
    }

    public Map<Object, Object> getRideStatus(String rideId) {
        return redisTemplate.opsForHash().entries(RIDE_KEY_PREFIX + rideId);
    }

    private double calculateFare(RideRequestDto dto) {
        double distanceKm = haversineDistance(
                dto.getPickupLat(), dto.getPickupLon(),
                dto.getDropLat(), dto.getDropLon());
        double multiplier = switch (dto.getRideType()) {
            case PREMIUM -> 1.8;
            case XL -> 1.5;
            default -> 1.0;
        };
        return Math.round((BASE_FARE + (distanceKm * PER_KM_RATE)) * multiplier * 100.0) / 100.0;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
