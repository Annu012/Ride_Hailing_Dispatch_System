package com.ridehailing.dispatch.controller;

import com.ridehailing.dispatch.service.DispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @GetMapping("/queue/depth")
    public ResponseEntity<Map<String, Integer>> queueDepth() {
        return ResponseEntity.ok(Map.of("depth", dispatchService.getQueueDepth()));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Dispatch Service is UP");
    }
}
