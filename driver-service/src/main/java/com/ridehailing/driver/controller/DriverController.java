package com.ridehailing.driver.controller;

import com.ridehailing.driver.dto.DriverRegistrationDto;
import com.ridehailing.driver.model.Driver;
import com.ridehailing.driver.model.DriverStatus;
import com.ridehailing.driver.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping("/register")
    public ResponseEntity<Driver> register(@RequestBody DriverRegistrationDto dto) {
        return ResponseEntity.ok(driverService.registerDriver(dto));
    }

    @PutMapping("/{driverId}/location")
    public ResponseEntity<String> updateLocation(@PathVariable String driverId,
                                                  @RequestBody Map<String, Double> location) {
        driverService.updateLocation(driverId, location.get("lat"), location.get("lon"));
        return ResponseEntity.ok("Location updated");
    }

    @PutMapping("/{driverId}/status")
    public ResponseEntity<String> updateStatus(@PathVariable String driverId,
                                                @RequestBody Map<String, String> body) {
        DriverStatus status = DriverStatus.valueOf(body.get("status"));
        driverService.advanceStatus(driverId, status);
        return ResponseEntity.ok("Status updated to " + status);
    }

    @GetMapping("/{driverId}")
    public ResponseEntity<Driver> getDriver(@PathVariable String driverId) {
        Driver driver = driverService.getDriver(driverId);
        if (driver == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(driver);
    }

    @GetMapping("/available")
    public ResponseEntity<List<Driver>> getAvailableDrivers() {
        return ResponseEntity.ok(driverService.getAvailableDrivers());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Driver Service is UP");
    }
}
