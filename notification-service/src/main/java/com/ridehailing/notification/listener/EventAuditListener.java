package com.ridehailing.notification.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class EventAuditListener {

    private final Counter rideRequestedCount;
    private final Counter rideAssignedCount;
    private final Counter rideCompletedCount;

    public EventAuditListener(MeterRegistry registry) {
        this.rideRequestedCount = Counter.builder("notification.ride.requested.total").register(registry);
        this.rideAssignedCount  = Counter.builder("notification.ride.assigned.total").register(registry);
        this.rideCompletedCount = Counter.builder("notification.ride.completed.total").register(registry);
    }

    @KafkaListener(topics = "ride-requested", groupId = "notification-service-group")
    public void onRideRequested(@Payload String payload,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        rideRequestedCount.increment();
        log.info("[AUDIT][{}] RIDE_REQUESTED key={} payload={}", Instant.now(), key, truncate(payload));
        // Extend here: send push notification to rider app, email, SMS via Twilio etc.
    }

    @KafkaListener(topics = "ride-assigned", groupId = "notification-service-group")
    public void onRideAssigned(@Payload String payload,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        rideAssignedCount.increment();
        log.info("[AUDIT][{}] RIDE_ASSIGNED key={} payload={}", Instant.now(), key, truncate(payload));
        // Extend here: notify rider that driver is on the way
    }

    @KafkaListener(topics = "ride-completed", groupId = "notification-service-group")
    public void onRideCompleted(@Payload String payload,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        rideCompletedCount.increment();
        log.info("[AUDIT][{}] RIDE_COMPLETED key={} payload={}", Instant.now(), key, truncate(payload));
        // Extend here: send receipt, trigger rating prompt
    }

    private String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
