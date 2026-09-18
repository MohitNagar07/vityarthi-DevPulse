package com.devpulse.service;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;
import com.devpulse.model.ServiceStatus;
import com.devpulse.repository.FileHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for aggregating metrics, computing SLA and uptime percentages,
 * measuring latency statistics, and dispatching incident alert logs.
 */
public class AnalyticsService {
    private final FileHandler fileHandler;
    private final Map<String, EndpointStats> statsMap;
    private final List<HealthCheckResult> globalHistory;

    /**
     * Initializes AnalyticsService with a file handler for incident reporting.
     *
     * @param fileHandler The persistence handler
     */
    public AnalyticsService(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
        this.statsMap = new ConcurrentHashMap<>();
        this.globalHistory = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Records a batch of health check results, updating statistics and triggering incident logs for failures.
     *
     * @param results List of check results from a polling cycle
     */
    public void recordBatch(List<HealthCheckResult> results) {
        for (HealthCheckResult res : results) {
            recordResult(res);
        }
    }

    /**
     * Records a single health check result, calculates latency metrics, and logs downtime events.
     *
     * @param result Result to record
     */
    public void recordResult(HealthCheckResult result) {
        if (result == null || result.getEndpoint() == null) return;

        globalHistory.add(result);
        String endpointId = result.getEndpoint().getId();

        statsMap.compute(endpointId, (id, existing) -> {
            EndpointStats stats = existing != null ? existing : new EndpointStats(result.getEndpoint());
            stats.addResult(result);
            return stats;
        });

        // Trigger incident log if health check is not UP (Downtime / SLA breach)
        if (!result.isUp()) {
            fileHandler.logIncident(result);
        }
    }

    /**
     * Retrieves statistics for all monitored endpoints.
     *
     * @return Collection of EndpointStats
     */
    public List<EndpointStats> getAllStats() {
        return new ArrayList<>(statsMap.values());
    }

    /**
     * Retrieves statistics for a specific endpoint ID.
     *
     * @param endpointId The endpoint ID
     * @return EndpointStats or null if no checks recorded yet
     */
    public EndpointStats getStats(String endpointId) {
        return statsMap.get(endpointId);
    }

    /**
     * Computes the global uptime percentage across all checks ever performed in the current session.
     *
     * @return Overall SLA percentage (0.0 to 100.0)
     */
    public double getGlobalUptimePercentage() {
        if (globalHistory.isEmpty()) {
            return 100.0;
        }
        long successCount = globalHistory.stream().filter(HealthCheckResult::isUp).count();
        return ((double) successCount / globalHistory.size()) * 100.0;
    }

    /**
     * Returns total number of checks performed across all endpoints in this session.
     *
     * @return Total checks count
     */
    public int getTotalChecksCount() {
        return globalHistory.size();
    }

    /**
     * Returns total number of failed checks (DOWN, TIMEOUT, ERROR).
     *
     * @return Total failed checks count
     */
    public int getTotalFailuresCount() {
        return (int) globalHistory.stream().filter(r -> !r.isUp()).count();
    }

    /**
     * Updates an endpoint reference in existing stats so changes to names/URLs are reflected immediately.
     *
     * @param updated The updated endpoint
     */
    public void updateEndpoint(Endpoint updated) {
        if (updated == null) return;
        EndpointStats stats = statsMap.get(updated.getId());
        if (stats != null) {
            stats.setEndpoint(updated);
        }
    }

    /**
     * Removes an endpoint from tracked statistics.
     *
     * @param endpointId The ID of the deleted endpoint
     */
    public void removeEndpoint(String endpointId) {
        if (endpointId != null) {
            statsMap.remove(endpointId.trim());
        }
    }

    /**
     * Inner class encapsulating aggregate statistical metrics for an individual endpoint.
     */
    public static class EndpointStats {
        private volatile Endpoint endpoint;
        private int totalChecks = 0;
        private int successfulChecks = 0;
        private int failedChecks = 0;
        private int validResponseChecks = 0;
        private long minLatencyMs = Long.MAX_VALUE;
        private long maxLatencyMs = 0;
        private long totalValidLatencyMs = 0;
        private HealthCheckResult lastResult = null;

        public EndpointStats(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public synchronized void addResult(HealthCheckResult res) {
            this.totalChecks++;
            this.lastResult = res;

            if (res.isUp()) {
                this.successfulChecks++;
            } else {
                this.failedChecks++;
            }

            // Benchmark latency only for probes that reached the server and returned a status code
            if (res.getStatusCode() > 0) {
                this.validResponseChecks++;
                long lat = res.getLatencyMs();
                if (lat < minLatencyMs) {
                    minLatencyMs = lat;
                }
                if (lat > maxLatencyMs) {
                    maxLatencyMs = lat;
                }
                totalValidLatencyMs += lat;
            }
        }

        public Endpoint getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public int getTotalChecks() {
            return totalChecks;
        }

        public int getSuccessfulChecks() {
            return successfulChecks;
        }

        public int getFailedChecks() {
            return failedChecks;
        }

        public long getMinLatencyMs() {
            return minLatencyMs == Long.MAX_VALUE ? 0 : minLatencyMs;
        }

        public long getMaxLatencyMs() {
            return maxLatencyMs;
        }

        public double getAverageLatencyMs() {
            return validResponseChecks == 0 ? 0.0 : (double) totalValidLatencyMs / validResponseChecks;
        }

        public double getUptimePercentage() {
            return totalChecks == 0 ? 100.0 : ((double) successfulChecks / totalChecks) * 100.0;
        }

        public HealthCheckResult getLastResult() {
            return lastResult;
        }

        public ServiceStatus getCurrentStatus() {
            return lastResult != null ? lastResult.getStatus() : ServiceStatus.ERROR;
        }
    }
}
