package com.devpulse.utils;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;
import com.devpulse.model.ServiceStatus;
import com.devpulse.service.AnalyticsService.EndpointStats;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility class providing ANSI color styling, ASCII banners, and tabular formatting
 * for the DevPulse CLI presentation layer.
 */
public class CLIFormatter {
    // ANSI Escape Sequences
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String UNDERLINE = "\u001B[4m";

    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String GRAY = "\u001B[90m";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Prints the primary application ASCII banner.
     */
    public static void printBanner() {
        System.out.println(CYAN + BOLD +
                "================================================================================" + RESET);
        System.out.println(CYAN + BOLD +
                "                         DevPulse Health Monitoring CLI                         " + RESET);
        System.out.println(CYAN + BOLD +
                "================================================================================" + RESET);
    }

    /**
     * Prints a section title header with consistent borders.
     *
     * @param title Title text to display
     */
    public static void printHeader(String title) {
        System.out.println();
        System.out.println(BLUE + BOLD + "--- [ " + title.toUpperCase() + " ] " + "-".repeat(Math.max(2, 65 - title.length())) + RESET);
    }

    /**
     * Colorizes latency based on response thresholds.
     *
     * @param latencyMs Latency duration in milliseconds
     * @return Colorized string with ms suffix
     */
    public static String formatLatency(long latencyMs) {
        if (latencyMs <= 0) {
            return GRAY + "N/A" + RESET;
        } else if (latencyMs < 250) {
            return GREEN + latencyMs + " ms" + RESET;
        } else if (latencyMs < 800) {
            return YELLOW + latencyMs + " ms" + RESET;
        } else {
            return RED + latencyMs + " ms" + RESET;
        }
    }

    /**
     * Colorizes SLA percentage according to enterprise service level thresholds.
     *
     * @param uptimePct Uptime percentage (0 - 100)
     * @return Colorized formatted percentage
     */
    public static String formatSla(double uptimePct) {
        String formatted = String.format("%.2f%%", uptimePct);
        if (uptimePct >= 99.0) {
            return GREEN + BOLD + formatted + RESET;
        } else if (uptimePct >= 95.0) {
            return YELLOW + BOLD + formatted + RESET;
        } else {
            return RED + BOLD + formatted + RESET;
        }
    }

    /**
     * Displays a clean formatted table of registered endpoints.
     *
     * @param endpoints List of endpoints
     */
    public static void printEndpointTable(List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            System.out.println(YELLOW + "  No endpoints registered. Choose option (2) to add one." + RESET);
            return;
        }

        String format = "| %-8s | %-24s | %-32s | %-6s | %-7s |\n";
        String border = "+----------+--------------------------+----------------------------------+--------+---------+";

        System.out.println(border);
        System.out.printf(format, "ID", "Service Name", "URL", "Expect", "Timeout");
        System.out.println(border);

        for (Endpoint ep : endpoints) {
            String shortUrl = ep.getUrl().length() > 32 ? ep.getUrl().substring(0, 29) + "..." : ep.getUrl();
            String shortName = ep.getName().length() > 24 ? ep.getName().substring(0, 21) + "..." : ep.getName();
            System.out.printf(format, ep.getId(), shortName, shortUrl, ep.getExpectedStatusCode(), ep.getTimeoutSeconds() + "s");
        }
        System.out.println(border);
        System.out.println(GRAY + " Total registered endpoints: " + endpoints.size() + RESET);
    }

    /**
     * Strips ANSI escape sequences from a string to determine true visible width.
     *
     * @param input Formatted text with ANSI escape codes
     * @return Raw unformatted text
     */
    public static String stripAnsi(String input) {
        if (input == null) return "";
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    /**
     * Pads a string with spaces to a target visible width, ignoring invisible ANSI escape codes.
     *
     * @param text               Input string (may contain ANSI color sequences)
     * @param targetVisibleWidth Desired column width on the terminal
     * @return Padded string ensuring perfect table alignment
     */
    public static String padRight(String text, int targetVisibleWidth) {
        if (text == null) text = "";
        int visibleLen = stripAnsi(text).length();
        int padding = Math.max(0, targetVisibleWidth - visibleLen);
        return text + " ".repeat(padding);
    }

    /**
     * Displays health check results in an ASCII dashboard table.
     *
     * @param results List of check results
     */
    public static void printHealthCheckResults(List<HealthCheckResult> results) {
        if (results == null || results.isEmpty()) {
            System.out.println(YELLOW + "  No health check results available." + RESET);
            return;
        }

        String border = "+----------+------------------------+--------------+--------+------------+----------+--------------------------+";
        System.out.println(border);
        System.out.printf("| %-8s | %-22s | %-12s | %-6s | %-10s | %-8s | %-24s |\n",
                "ID", "Service Name", "Status", "Code", "Latency", "Time", "Diagnostics");
        System.out.println(border);

        for (HealthCheckResult res : results) {
            Endpoint ep = res.getEndpoint();
            String shortName = ep.getName().length() > 22 ? ep.getName().substring(0, 19) + "..." : ep.getName();
            String codeStr = res.getStatusCode() > 0 ? String.valueOf(res.getStatusCode()) : "-";
            String diag = res.getMessage().length() > 24 ? res.getMessage().substring(0, 21) + "..." : res.getMessage();
            String timeStr = res.getTimestamp().format(TIME_FORMAT);

            String statusCell = padRight(res.getStatus().getColoredLabel(), 12);
            String latencyCell = padRight(formatLatency(res.getLatencyMs()), 10);

            System.out.printf("| %-8s | %-22s | %s | %-6s | %s | %-8s | %-24s |\n",
                    ep.getId(),
                    shortName,
                    statusCell,
                    codeStr,
                    latencyCell,
                    timeStr,
                    diag);
        }
        System.out.println(border);
    }

    /**
     * Displays aggregated SLA and latency analytics in a formatted table.
     *
     * @param statsList List of EndpointStats
     * @param globalSla Global SLA percentage
     */
    public static void printAnalyticsDashboard(List<EndpointStats> statsList, double globalSla, int totalChecks, int totalFailures) {
        if (statsList == null || statsList.isEmpty()) {
            System.out.println(YELLOW + "  No analytics data recorded yet. Run a health check first." + RESET);
            return;
        }

        System.out.println(BOLD + "\n============================= GLOBAL HEALTH SUMMARY =============================" + RESET);
        System.out.printf("  Total Invocations: %s%-6d%s | Failures: %s%-6d%s | Overall Session SLA: %s\n",
                CYAN + BOLD, totalChecks, RESET,
                totalFailures > 0 ? RED + BOLD : GREEN + BOLD, totalFailures, RESET,
                formatSla(globalSla));
        System.out.println(BOLD + "=================================================================================" + RESET);

        String border = "+----------+------------------------+------------+--------+--------+----------+----------+----------+";
        System.out.println(border);
        System.out.printf("| %-8s | %-22s | %-10s | %-6s | %-6s | %-8s | %-8s | %-8s |\n",
                "ID", "Service Name", "Uptime SLA", "Pings", "Fails", "Min Lat", "Avg Lat", "Max Lat");
        System.out.println(border);

        for (EndpointStats s : statsList) {
            String shortName = s.getEndpoint().getName().length() > 22 ? 
                    s.getEndpoint().getName().substring(0, 19) + "..." : s.getEndpoint().getName();

            String slaCell = padRight(formatSla(s.getUptimePercentage()), 10);

            System.out.printf("| %-8s | %-22s | %s | %-6d | %-6d | %-8s | %-8s | %-8s |\n",
                    s.getEndpoint().getId(),
                    shortName,
                    slaCell,
                    s.getTotalChecks(),
                    s.getFailedChecks(),
                    s.getMinLatencyMs() + "ms",
                    String.format("%.1fms", s.getAverageLatencyMs()),
                    s.getMaxLatencyMs() + "ms");
        }
        System.out.println(border);
    }
}
