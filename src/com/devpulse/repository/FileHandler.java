package com.devpulse.repository;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Layer responsible for file I/O operations.
 * Manages persistent storage of endpoint configurations in CSV format
 * and incident logging in plain text format.
 */
public class FileHandler {
    private static final String CSV_HEADER = "# id,name,url,expectedStatusCode,timeoutSeconds";
    private final Path endpointsFilePath;
    private final Path incidentLogFilePath;

    /**
     * Default constructor pointing to standard data and logs directories.
     */
    public FileHandler() {
        this(Paths.get("data", "endpoints.csv"), Paths.get("logs", "health_events.log"));
    }

    /**
     * Parameterized constructor for custom paths (useful for testing).
     *
     * @param endpointsFilePath   Path to the endpoints CSV file
     * @param incidentLogFilePath Path to the health events log file
     */
    public FileHandler(Path endpointsFilePath, Path incidentLogFilePath) {
        this.endpointsFilePath = endpointsFilePath;
        this.incidentLogFilePath = incidentLogFilePath;
        initializeStorage();
    }

    /**
     * Ensures necessary parent directories exist and seeds default endpoints if the registry file is absent.
     */
    private void initializeStorage() {
        try {
            if (endpointsFilePath.getParent() != null) {
                Files.createDirectories(endpointsFilePath.getParent());
            }
            if (incidentLogFilePath.getParent() != null) {
                Files.createDirectories(incidentLogFilePath.getParent());
            }

            if (Files.notExists(endpointsFilePath)) {
                createDefaultEndpointsFile();
            }
        } catch (IOException e) {
            System.err.println("Warning: Unable to initialize storage directories: " + e.getMessage());
        }
    }

    /**
     * Creates an initial seed configuration with well-known public test endpoints.
     */
    private void createDefaultEndpointsFile() {
        List<Endpoint> defaults = new ArrayList<>();
        defaults.add(new Endpoint("EP-101", "Cloudflare Trace", "https://1.1.1.1/cdn-cgi/trace", 200, 5));
        defaults.add(new Endpoint("EP-102", "Google Public DNS", "https://dns.google", 200, 5));
        defaults.add(new Endpoint("EP-103", "JSONPlaceholder API", "https://jsonplaceholder.typicode.com/posts/1", 200, 5));
        defaults.add(new Endpoint("EP-104", "HTTPBin Success (200)", "https://httpbin.org/status/200", 200, 5));
        defaults.add(new Endpoint("EP-105", "HTTPBin Simulated Failure (500)", "https://httpbin.org/status/500", 200, 5));

        try {
            saveEndpoints(defaults);
        } catch (IOException e) {
            System.err.println("Failed to seed default endpoints: " + e.getMessage());
        }
    }

    /**
     * Loads all configured endpoints from the CSV file.
     *
     * @return List of parsed Endpoint objects
     * @throws IOException If file reading fails
     */
    public List<Endpoint> loadEndpoints() throws IOException {
        List<Endpoint> endpoints = new ArrayList<>();
        if (Files.notExists(endpointsFilePath)) {
            return endpoints;
        }

        try (BufferedReader reader = Files.newBufferedReader(endpointsFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Endpoint endpoint = Endpoint.fromCsv(line);
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        }
        return endpoints;
    }

    /**
     * Persists the given list of endpoints to the CSV file safely using an atomic temporary file swap.
     * Prevents file corruption if the application terminates mid-write.
     *
     * @param endpoints List of endpoints to write
     * @throws IOException If file writing fails
     */
    public synchronized void saveEndpoints(List<Endpoint> endpoints) throws IOException {
        Path tempFile = endpointsFilePath.resolveSibling(endpointsFilePath.getFileName() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (Endpoint ep : endpoints) {
                writer.write(ep.toCsv());
                writer.newLine();
            }
        }

        try {
            Files.move(tempFile, endpointsFilePath, 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempFile, endpointsFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Appends an incident or downtime event to the health_events.log file.
     * Synchronized to ensure thread-safe log appending.
     *
     * @param result Health check result representing the failure or status change
     */
    public synchronized void logIncident(HealthCheckResult result) {
        if (result == null) return;
        try (BufferedWriter writer = Files.newBufferedWriter(incidentLogFilePath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(result.toLogEntry());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Warning: Failed to write to incident log: " + e.getMessage());
        }
    }

    /**
     * Retrieves the most recent log entries from the health_events.log file using a bounded queue.
     * Protects against OutOfMemoryError when log files grow large.
     *
     * @param maxLines Maximum number of lines to retrieve from the tail
     * @return List of recent log lines
     */
    public List<String> readRecentLogs(int maxLines) {
        List<String> result = new ArrayList<>();
        if (Files.notExists(incidentLogFilePath) || maxLines <= 0) {
            return result;
        }

        java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>(maxLines);
        try (BufferedReader reader = Files.newBufferedReader(incidentLogFilePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tail.size() >= maxLines) {
                    tail.pollFirst();
                }
                tail.offerLast(line);
            }
            result.addAll(tail);
        } catch (IOException e) {
            result.add("Error reading incident logs: " + e.getMessage());
        }
        return result;
    }

    public Path getEndpointsFilePath() {
        return endpointsFilePath;
    }

    public Path getIncidentLogFilePath() {
        return incidentLogFilePath;
    }
}
