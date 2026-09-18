package com.devpulse.model;

/**
 * Enumeration representing the possible health statuses of a monitored service or API endpoint.
 * Includes visual indicators and color codes for terminal display.
 */
public enum ServiceStatus {
    UP("[OK] UP", "\u001B[32m"),           // Green
    DOWN("[FAIL] DOWN", "\u001B[31m"),     // Red
    TIMEOUT("[WARN] TIMEOUT", "\u001B[33m"), // Yellow
    ERROR("[ERR] ERROR", "\u001B[35m");    // Magenta

    private final String label;
    private final String ansiColor;
    private static final String ANSI_RESET = "\u001B[0m";

    ServiceStatus(String label, String ansiColor) {
        this.label = label;
        this.ansiColor = ansiColor;
    }

    /**
     * Gets the display label with symbolic indicator.
     *
     * @return Label string (e.g. "[✓] UP")
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the formatted label wrapped in ANSI color sequences.
     *
     * @return Colorized label string
     */
    public String getColoredLabel() {
        return ansiColor + label + ANSI_RESET;
    }

    /**
     * Determines whether the status represents a healthy operational state.
     *
     * @return true if status is UP, false otherwise
     */
    public boolean isHealthy() {
        return this == UP;
    }
}
