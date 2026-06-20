package com.ridehailing.rider.controller;

import com.ridehailing.rider.dto.RideEvent;
import com.ridehailing.rider.dto.RideRequestDto;
import com.ridehailing.rider.service.RiderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    @PostMapping("/request")
    public ResponseEntity<RideEvent> requestRide(@Valid @RequestBody RideRequestDto dto) {
        RideEvent event = riderService.requestRide(dto);
        return ResponseEntity.ok(event);
    }

    @GetMapping("/{rideId}/status")
    public ResponseEntity<Map<Object, Object>> getRideStatus(@PathVariable String rideId) {
        Map<Object, Object> status = riderService.getRideStatus(rideId);
        if (status.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Rider Service is UP");
    }
}
