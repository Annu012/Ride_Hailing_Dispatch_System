package com.ridehailing.dispatch.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.dispatch.dto.RideEvent;
import com.ridehailing.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestListener {

    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @KafkaListener(topics = "ride-requested", groupId = "dispatch-service-group")
    public void onRideRequested(@Payload String payload,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String rideId) {
        try {
            RideEvent event = objectMapper.readValue(payload, RideEvent.class);
            log.info("Ride request received: {} for rider: {}", rideId, event.getRiderId());
            dispatchService.enqueueRide(event);
        } catch (Exception e) {
            log.error("Error processing ride request {}: {}", rideId, e.getMessage());
        }
    }
}
