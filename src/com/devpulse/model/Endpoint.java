package com.devpulse.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Model representing a monitored server or API endpoint.
 * Contains endpoint metadata, target URL, expected HTTP status code, and timeout configuration.
 */
public class Endpoint {
    private String id;
    private String name;
    private String url;
    private int expectedStatusCode;
    private int timeoutSeconds;

    /**
     * Default constructor.
     */
    public Endpoint() {
        this.expectedStatusCode = 200;
        this.timeoutSeconds = 5;
    }

    /**
     * Parameterized constructor.
     *
     * @param id                 Unique identifier for the endpoint
     * @param name               Descriptive label for the service
     * @param url                Target HTTP/HTTPS URL
     * @param expectedStatusCode Expected HTTP status code (typically 200)
     * @param timeoutSeconds     Request timeout threshold in seconds
     */
    public Endpoint(String id, String name, String url, int expectedStatusCode, int timeoutSeconds) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.expectedStatusCode = expectedStatusCode <= 0 ? 200 : expectedStatusCode;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 5 : timeoutSeconds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public void setExpectedStatusCode(int expectedStatusCode) {
        this.expectedStatusCode = expectedStatusCode;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Converts the endpoint to a CSV line format.
     *
     * @return Formatted CSV record
     */
    public String toCsv() {
        return String.format("%s,%s,%s,%d,%d",
                escapeCsv(id),
                escapeCsv(name),
                escapeCsv(url),
                expectedStatusCode,
                timeoutSeconds);
    }

    /**
     * Parses a CSV line into an Endpoint entity following RFC-4180 rules.
     * Correctly handles quoted fields containing commas and escaped quotes.
     *
     * @param line CSV row
     * @return Endpoint instance or null if line is invalid
     */
    public static Endpoint fromCsv(String line) {
        if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
            return null;
        }

        List<String> tokens = parseCsvLine(line.trim());
        if (tokens.size() < 5) {
            return null;
        }

        try {
            String id = tokens.get(0).trim();
            String name = tokens.get(1).trim();
            String url = tokens.get(2).trim();
            int expectedStatus = Integer.parseInt(tokens.get(3).trim());
            int timeout = Integer.parseInt(tokens.get(4).trim());
            return new Endpoint(id, name, url, expectedStatus, timeout);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Tokenizes a CSV line respecting quoted strings and escaped quotes.
     *
     * @param line Raw CSV string
     * @return List of parsed field tokens
     */
    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote: "" -> "
                    sb.append('"');
                    i++;
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens;
    }

    private static String escapeCsv(String input) {
        if (input == null) return "";
        if (input.contains(",") || input.contains("\"") || input.contains("\n") || input.contains("\r")) {
            return "\"" + input.replace("\"", "\"\"") + "\"";
        }
        return input;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return Objects.equals(id, endpoint.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("Endpoint[id=%s, name=%s, url=%s, expectedCode=%d, timeout=%ds]",
                id, name, url, expectedStatusCode, timeoutSeconds);
    }
}
