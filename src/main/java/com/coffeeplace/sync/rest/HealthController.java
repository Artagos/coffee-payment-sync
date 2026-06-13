package com.coffeeplace.sync.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class HealthController {

    private final String instanceId;
    private final long startTime = System.currentTimeMillis();
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public HealthController(@Value("${INSTANCE_ID:${instance.id:default}}") String instanceId) {
        this.instanceId = instanceId + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "instanceId", instanceId
        ));
    }

    @GetMapping("/api/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        long uptime = System.currentTimeMillis() - startTime;
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double cpuLoad = osBean.getSystemLoadAverage();

        return ResponseEntity.ok(Map.of(
                "instanceId", instanceId,
                "status", "UP",
                "uptime", uptime,
                "heapUsed", heapUsed,
                "heapMax", heapMax,
                "cpuLoad", cpuLoad >= 0 ? cpuLoad : 0.0,
                "activeRequests", activeRequests.get()
        ));
    }

    public AtomicInteger getActiveRequests() {
        return activeRequests;
    }
}
