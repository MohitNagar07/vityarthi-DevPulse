package com.devpulse.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing the outcome of a single health check ping for an endpoint.
 * Tracks response timing, HTTP status code, operational health status, and diagnostic messages.
 */
public class HealthCheckResult {
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Endpoint endpoint;
    private final LocalDateTime timestamp;
    private final int statusCode;
    private final long latencyMs;
    private final ServiceStatus status;
    private final String message;

    /**
     * Constructs a HealthCheckResult instance.
     *
     * @param endpoint   The monitored endpoint
     * @param timestamp  Execution timestamp
     * @param statusCode HTTP status code returned (or 0 if connection failed)
     * @param latencyMs  Total response duration in milliseconds
     * @param status     Assessed operational status
     * @param message    Diagnostic or error message
     */
    public HealthCheckResult(Endpoint endpoint, LocalDateTime timestamp, int statusCode, 
                             long latencyMs, ServiceStatus status, String message) {
        this.endpoint = endpoint;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.status = status;
        this.message = message != null ? message : "";
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Helper to verify if the health check succeeded.
     *
     * @return true if status is UP
     */
    public boolean isUp() {
        return status == ServiceStatus.UP;
    }

    /**
     * Formats the result as an incident log entry suitable for persistence.
     *
     * @return Formatted log line
     */
    public String toLogEntry() {
        return String.format("[%s] [STATUS: %s] [ID: %s] [NAME: %s] [URL: %s] [CODE: %s] [LATENCY: %dms] - %s",
                timestamp.format(LOG_DATE_FORMAT),
                status.name(),
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                statusCode > 0 ? String.valueOf(statusCode) : "N/A",
                latencyMs,
                message);
    }

    @Override
    public String toString() {
        return String.format("HealthCheckResult[%s | %s | %dms | Code: %d | %s]",
                endpoint.getName(),
                status.getLabel(),
                latencyMs,
                statusCode,
                message);
    }
}
