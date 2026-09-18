package com.devpulse.service;

import com.devpulse.model.Endpoint;
import com.devpulse.repository.FileHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service handling business logic for endpoint registration and CRUD operations.
 * Maintains in-memory registry synchronized with persistent file storage.
 */
public class RegistryService {
    private final FileHandler fileHandler;
    private final List<Endpoint> endpointList;
    private final AtomicInteger idCounter;

    /**
     * Initializes RegistryService with default FileHandler and loads initial state.
     */
    public RegistryService() {
        this(new FileHandler());
    }

    /**
     * Parameterized constructor for dependency injection.
     *
     * @param fileHandler The persistence handler
     */
    public RegistryService(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
        this.endpointList = new CopyOnWriteArrayList<>();
        this.idCounter = new AtomicInteger(100);
        reload();
    }

    /**
     * Reloads endpoints from the underlying persistent storage.
     */
    public synchronized void reload() {
        try {
            List<Endpoint> loaded = fileHandler.loadEndpoints();
            endpointList.clear();
            endpointList.addAll(loaded);

            // Determine maximum existing ID to avoid collision
            int maxId = 100;
            for (Endpoint ep : loaded) {
                if (ep.getId() != null && ep.getId().startsWith("EP-")) {
                    try {
                        int num = Integer.parseInt(ep.getId().substring(3));
                        if (num > maxId) {
                            maxId = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            idCounter.set(maxId);
        } catch (IOException e) {
            System.err.println("Failed to load endpoints from storage: " + e.getMessage());
        }
    }

    /**
     * Retrieves an unmodifiable view of all registered endpoints.
     *
     * @return List of endpoints
     */
    public List<Endpoint> getAllEndpoints() {
        return Collections.unmodifiableList(new ArrayList<>(endpointList));
    }

    /**
     * Finds an endpoint by its unique identifier.
     *
     * @param id The endpoint ID
     * @return Optional containing Endpoint if found
     */
    public Optional<Endpoint> getEndpointById(String id) {
        if (id == null) return Optional.empty();
        return endpointList.stream()
                .filter(ep -> ep.getId().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    /**
     * Validates whether a URL string is syntactically sound and uses HTTP/HTTPS.
     *
     * @param url URL string to test
     * @return true if valid HTTP/HTTPS URL
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && !host.trim().isEmpty() && !host.contains(" ");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Registers a new endpoint, persists it to storage, and assigns an ID.
     *
     * @param name               Display name for the endpoint
     * @param url                Target URL
     * @param expectedStatusCode Expected HTTP status code (e.g., 200)
     * @param timeoutSeconds     Timeout threshold in seconds
     * @return The created Endpoint
     * @throws IllegalArgumentException If parameters violate validation bounds
     * @throws IOException              If persistence fails
     */
    public synchronized Endpoint addEndpoint(String name, String url, int expectedStatusCode, int timeoutSeconds) 
            throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint name cannot be empty.");
        }
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL. Must be a valid http:// or https:// address.");
        }
        if (expectedStatusCode < 100 || expectedStatusCode > 599) {
            throw new IllegalArgumentException("Expected status code must be between 100 and 599.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 60) {
            throw new IllegalArgumentException("Timeout must be between 1 and 60 seconds.");
        }

        String id = "EP-" + idCounter.incrementAndGet();
        Endpoint endpoint = new Endpoint(id, name.trim(), url.trim(), expectedStatusCode, timeoutSeconds);

        endpointList.add(endpoint);
        fileHandler.saveEndpoints(new ArrayList<>(endpointList));
        return endpoint;
    }

    /**
     * Updates an existing endpoint and saves changes to disk.
     *
     * @param id                 Endpoint ID to update
     * @param newName            New name (null or blank to retain existing)
     * @param newUrl             New URL (null or blank to retain existing)
     * @param newExpectedStatus  New status code (null or <= 0 to retain existing)
     * @param newTimeoutSeconds  New timeout (null or <= 0 to retain existing)
     * @return true if updated, false if endpoint not found
     * @throws IllegalArgumentException If parameters violate validation bounds
     * @throws IOException              If saving to disk fails
     */
    public synchronized boolean updateEndpoint(String id, String newName, String newUrl, 
                                               Integer newExpectedStatus, Integer newTimeoutSeconds) 
            throws IOException {
        Optional<Endpoint> opt = getEndpointById(id);
        if (opt.isEmpty()) {
            return false;
        }

        Endpoint ep = opt.get();
        if (newName != null && !newName.trim().isEmpty()) {
            ep.setName(newName.trim());
        }
        if (newUrl != null && !newUrl.trim().isEmpty()) {
            if (!isValidUrl(newUrl)) {
                throw new IllegalArgumentException("Invalid URL: " + newUrl);
            }
            ep.setUrl(newUrl.trim());
        }
        if (newExpectedStatus != null && newExpectedStatus > 0) {
            if (newExpectedStatus < 100 || newExpectedStatus > 599) {
                throw new IllegalArgumentException("Status code must be between 100 and 599.");
            }
            ep.setExpectedStatusCode(newExpectedStatus);
        }
        if (newTimeoutSeconds != null && newTimeoutSeconds > 0) {
            if (newTimeoutSeconds < 1 || newTimeoutSeconds > 60) {
                throw new IllegalArgumentException("Timeout must be between 1 and 60 seconds.");
            }
            ep.setTimeoutSeconds(newTimeoutSeconds);
        }

        fileHandler.saveEndpoints(new ArrayList<>(endpointList));
        return true;
    }

    /**
     * Deletes an endpoint by ID and updates persistent storage.
     *
     * @param id The endpoint ID to remove
     * @return true if removed, false if not found
     * @throws IOException If saving to disk fails
     */
    public synchronized boolean deleteEndpoint(String id) throws IOException {
        Optional<Endpoint> opt = getEndpointById(id);
        if (opt.isEmpty()) {
            return false;
        }

        boolean removed = endpointList.remove(opt.get());
        if (removed) {
            fileHandler.saveEndpoints(new ArrayList<>(endpointList));
        }
        return removed;
    }

    public FileHandler getFileHandler() {
        return fileHandler;
    }
}
