package com.ridehailing.driver.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.driver.dto.AssignmentEvent;
import com.ridehailing.driver.service.DriverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AssignmentListener {

    private static final String RIDE_KEY_PREFIX = "ride:";

    private final DriverService driverService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ✅ Constructor injection (replacing @RequiredArgsConstructor) so we can
    //    inject RedisTemplate alongside DriverService.
    public AssignmentListener(DriverService driverService,
                               RedisTemplate<String, Object> redisTemplate) {
        this.driverService = driverService;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "ride-assigned", groupId = "driver-service-group")
    public void onAssignment(@Payload String payload,
                              @Header(KafkaHeaders.RECEIVED_KEY) String rideId) {
        try {
            // ✅ FIX: Defensively unwrap double-encoded JSON string.
            // Before the DispatchService fix, the payload arrived as "\"{ ... }\"" —
            // a JSON string whose value is the JSON object. After the fix it arrives
            // as "{ ... }" directly. The trim+unwrap below handles both cases safely
            // so a partial deploy (dispatch fixed, driver not yet redeployed) won't break.
            String json = payload.trim();
            if (json.startsWith("\"") && json.endsWith("\"")) {
                // Strip outer quotes and unescape inner content
                json = objectMapper.readValue(json, String.class);
            }

            AssignmentEvent event = objectMapper.readValue(json, AssignmentEvent.class);
            log.info("Assignment received: ride={} driver={}", event.getRideId(), event.getDriverId());

            // Apply FSM transition: AVAILABLE → ASSIGNED on the driver record
            boolean applied = driverService.applyAssignment(event);
            if (!applied) {
                log.warn("Could not apply assignment for driver {}", event.getDriverId());
                return;
            }

            // ✅ FIX: Write assignment status back to the ride's Redis hash so that
            //    rider-service's GET /rides/{id}/status reflects the updated state.
            //    Key format matches RiderService: "ride:{rideId}"
            //    Field names match the Map.of(...) in RiderService.requestRide().
            String rideKey = RIDE_KEY_PREFIX + event.getRideId();
            redisTemplate.opsForHash().put(rideKey, "status",    "ASSIGNED");
            redisTemplate.opsForHash().put(rideKey, "driverId",  event.getDriverId());
            redisTemplate.opsForHash().put(rideKey, "driverName", event.getDriverName());
            redisTemplate.opsForHash().put(rideKey, "vehicleNumber", event.getVehicleNumber());
            redisTemplate.opsForHash().put(rideKey, "distanceKm", String.valueOf(event.getDistanceKm()));

            log.info("✅ Ride {} status updated to ASSIGNED (driver: {})",
                    event.getRideId(), event.getDriverId());

        } catch (Exception e) {
            log.error("Error processing assignment event: {}", e.getMessage());
        }
    }
}
