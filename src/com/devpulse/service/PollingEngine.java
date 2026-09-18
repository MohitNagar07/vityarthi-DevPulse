package com.devpulse.service;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;
import com.devpulse.model.ServiceStatus;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service responsible for concurrent asynchronous network polling and health metrics collection.
 * Utilizes Java's standard HttpClient and CompletableFuture for non-blocking parallel execution.
 */
public class PollingEngine {
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final boolean customExecutor;

    /**
     * Initializes the polling engine with a bounded thread pool and tuned HttpClient.
     */
    public PollingEngine() {
        this(Executors.newFixedThreadPool(
                Math.min(32, Math.max(4, Runtime.getRuntime().availableProcessors() * 2)),
                r -> {
                    Thread t = new Thread(r, "DevPulse-Poller");
                    t.setDaemon(true);
                    return t;
                }), true);
    }

    /**
     * Parameterized constructor for custom executor injection.
     *
     * @param executorService Custom thread pool executor
     * @param customExecutor  Flag indicating if this class owns the executor lifecycle
     */
    public PollingEngine(ExecutorService executorService, boolean customExecutor) {
        this.executorService = executorService;
        this.customExecutor = customExecutor;
        this.httpClient = HttpClient.newBuilder()
                .executor(this.executorService)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Performs a health check ping against a single endpoint.
     * Accurately measures response latency and maps network outcomes to ServiceStatus.
     *
     * @param endpoint The target endpoint to probe
     * @return HealthCheckResult containing status, latency, and diagnostics
     */
    public HealthCheckResult checkEndpoint(Endpoint endpoint) {
        LocalDateTime timestamp = LocalDateTime.now();
        if (endpoint == null || endpoint.getUrl() == null || endpoint.getUrl().trim().isEmpty()) {
            return new HealthCheckResult(
                    endpoint != null ? endpoint : new Endpoint("UNKNOWN", "Unknown", "", 200, 5),
                    timestamp, 0, 0, ServiceStatus.ERROR, "Endpoint or URL is null or empty");
        }

        long startTime = System.nanoTime();
        int timeoutSec = Math.max(1, Math.min(60, endpoint.getTimeoutSeconds()));

        try {
            URI targetUri = URI.create(endpoint.getUrl().trim());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("User-Agent", "DevPulse-HealthChecker/1.0")
                    .GET()
                    .build();

            // Using discarding() body handler to avoid unnecessary memory allocations
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            int actualCode = response.statusCode();
            int expectedCode = endpoint.getExpectedStatusCode();

            if (actualCode == expectedCode) {
                return new HealthCheckResult(endpoint, timestamp, actualCode, elapsedMs,
                        ServiceStatus.UP, "Healthy (HTTP " + actualCode + ")");
            } else {
                return new HealthCheckResult(endpoint, timestamp, actualCode, elapsedMs,
                        ServiceStatus.DOWN, "Unexpected status: expected " + expectedCode + ", got " + actualCode);
            }
        } catch (HttpTimeoutException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.TIMEOUT, "Request timed out after " + timeoutSec + "s");
        } catch (UnknownHostException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.ERROR, "DNS resolution failed: unknown host");
        } catch (ConnectException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.DOWN, "Connection refused: server is unreachable");
        } catch (javax.net.ssl.SSLException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.ERROR, "SSL handshake failed: certificate invalid or untrusted");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.ERROR, "Health check interrupted");
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new HealthCheckResult(endpoint, timestamp, 0, elapsedMs,
                    ServiceStatus.ERROR, "Error: " + errorMsg);
        }
    }

    /**
     * Concurrently checks all provided endpoints in parallel.
     * Completes in approximately the time of the slowest single request.
     *
     * @param endpoints List of endpoints to probe
     * @return List of HealthCheckResults for each endpoint
     */
    public List<HealthCheckResult> checkAllConcurrently(List<Endpoint> endpoints) {
        List<CompletableFuture<HealthCheckResult>> futures = endpoints.stream()
                .map(ep -> CompletableFuture.supplyAsync(() -> checkEndpoint(ep), executorService))
                .collect(Collectors.toList());

        // Wait for all asynchronous checks to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /**
     * Gracefully shuts down the executor service if managed by this instance.
     */
    public void shutdown() {
        if (customExecutor && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
