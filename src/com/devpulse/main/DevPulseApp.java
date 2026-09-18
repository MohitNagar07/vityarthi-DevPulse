package com.devpulse.main;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;
import com.devpulse.repository.FileHandler;
import com.devpulse.service.AnalyticsService;
import com.devpulse.service.PollingEngine;
import com.devpulse.service.RegistryService;
import com.devpulse.utils.CLIFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Main application entry point for DevPulse: Server & API Health Monitoring CLI.
 * Coordinates the Presentation, Service, and Data Access layers through an interactive terminal interface.
 */
public class DevPulseApp {
    private final RegistryService registryService;
    private final PollingEngine pollingEngine;
    private final AnalyticsService analyticsService;
    private final FileHandler fileHandler;
    private final Scanner scanner;

    public DevPulseApp() {
        this.fileHandler = new FileHandler();
        this.registryService = new RegistryService(fileHandler);
        this.pollingEngine = new PollingEngine();
        this.analyticsService = new AnalyticsService(fileHandler);
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        DevPulseApp app = new DevPulseApp();

        // Non-interactive automated smoke test flag for CI/CD or verification
        if (args.length > 0 && "--check-once".equalsIgnoreCase(args[0])) {
            app.runSingleBatchCheck();
            app.shutdown();
            return;
        }

        app.run();
    }

    /**
     * Safely reads a line from the scanner, gracefully handling EOF or pipe termination.
     *
     * @return Trimmed input string, or null if input stream is exhausted
     */
    private String readLineSafe() {
        try {
            if (scanner == null || !scanner.hasNextLine()) {
                return null;
            }
            return scanner.nextLine().trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Primary interactive command loop.
     */
    public void run() {
        CLIFormatter.printBanner();

        boolean running = true;
        while (running) {
            printMainMenu();
            System.out.print(CLIFormatter.CYAN + CLIFormatter.BOLD + "\nSelect an option [0-9]: " + CLIFormatter.RESET);
            String input = readLineSafe();

            if (input == null || "0".equals(input) || "exit".equalsIgnoreCase(input) || "q".equalsIgnoreCase(input)) {
                running = false;
                System.out.println(CLIFormatter.GREEN + "\nShutting down DevPulse. Goodbye!" + CLIFormatter.RESET);
                break;
            }

            switch (input) {
                case "1":
                    handleListEndpoints();
                    break;
                case "2":
                    handleAddEndpoint();
                    break;
                case "3":
                    handleUpdateEndpoint();
                    break;
                case "4":
                    handleDeleteEndpoint();
                    break;
                case "5":
                    handleRunHealthCheck();
                    break;
                case "6":
                    handleContinuousMonitoring();
                    break;
                case "7":
                    handleViewAnalytics();
                    break;
                case "8":
                    handleViewIncidentLogs();
                    break;
                case "9":
                    handleReloadRegistry();
                    break;
                default:
                    System.out.println(CLIFormatter.RED + "Invalid option. Please enter a number between 0 and 9." + CLIFormatter.RESET);
            }
        }

        shutdown();
    }

    private void printMainMenu() {
        System.out.println(CLIFormatter.BOLD + "\n+------------------------ MAIN MENU ------------------------+" + CLIFormatter.RESET);
        System.out.println("  1. List Registered Endpoints");
        System.out.println("  2. Add New Endpoint");
        System.out.println("  3. Update Existing Endpoint");
        System.out.println("  4. Delete Endpoint");
        System.out.println("  5. Run Health Check (Concurrent Ping All)");
        System.out.println("  6. Start Continuous Real-Time Monitor");
        System.out.println("  7. View Analytics & SLA Summary Report");
        System.out.println("  8. View Incident Logs (health_events.log)");
        System.out.println("  9. Reload Configuration from Disk");
        System.out.println("  0. Exit Application");
        System.out.println(CLIFormatter.BOLD + "+-----------------------------------------------------------+" + CLIFormatter.RESET);
    }

    private void handleListEndpoints() {
        CLIFormatter.printHeader("Registered Endpoints");
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        CLIFormatter.printEndpointTable(endpoints);
    }

    private void handleAddEndpoint() {
        CLIFormatter.printHeader("Add New Endpoint");
        try {
            System.out.print("Enter descriptive name (e.g. Payments Gateway): ");
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) {
                System.out.println(CLIFormatter.RED + "Error: Name cannot be empty." + CLIFormatter.RESET);
                return;
            }

            System.out.print("Enter target URL (e.g. https://api.example.com/health): ");
            String url = scanner.nextLine().trim();
            if (!RegistryService.isValidUrl(url)) {
                System.out.println(CLIFormatter.RED + "Error: Invalid URL. Must begin with http:// or https://" + CLIFormatter.RESET);
                return;
            }

            System.out.print("Enter expected HTTP status code [default 200]: ");
            String codeInput = scanner.nextLine().trim();
            int statusCode = codeInput.isEmpty() ? 200 : Integer.parseInt(codeInput);

            System.out.print("Enter timeout in seconds [default 5]: ");
            String timeoutInput = scanner.nextLine().trim();
            int timeout = timeoutInput.isEmpty() ? 5 : Integer.parseInt(timeoutInput);

            Endpoint created = registryService.addEndpoint(name, url, statusCode, timeout);
            System.out.println(CLIFormatter.GREEN + "\n[SUCCESS] Endpoint created successfully: " + created.getId() + " (" + created.getName() + ")" + CLIFormatter.RESET);
        } catch (NumberFormatException e) {
            System.out.println(CLIFormatter.RED + "Error: Numeric input required for status code and timeout." + CLIFormatter.RESET);
        } catch (Exception e) {
            System.out.println(CLIFormatter.RED + "Failed to add endpoint: " + e.getMessage() + CLIFormatter.RESET);
        }
    }

    private void handleUpdateEndpoint() {
        CLIFormatter.printHeader("Update Endpoint");
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        CLIFormatter.printEndpointTable(endpoints);

        if (endpoints.isEmpty()) return;

        System.out.print("\nEnter Endpoint ID to update (e.g. EP-101): ");
        String id = scanner.nextLine().trim();

        Optional<Endpoint> existing = registryService.getEndpointById(id);
        if (existing.isEmpty()) {
            System.out.println(CLIFormatter.RED + "Endpoint with ID '" + id + "' not found." + CLIFormatter.RESET);
            return;
        }

        Endpoint ep = existing.get();
        System.out.println(CLIFormatter.GRAY + "Press Enter without typing to keep current value." + CLIFormatter.RESET);

        try {
            System.out.print("New name [" + ep.getName() + "]: ");
            String newName = scanner.nextLine().trim();

            System.out.print("New URL [" + ep.getUrl() + "]: ");
            String newUrl = scanner.nextLine().trim();

            System.out.print("New expected status code [" + ep.getExpectedStatusCode() + "]: ");
            String statusInput = scanner.nextLine().trim();
            Integer newStatus = statusInput.isEmpty() ? null : Integer.parseInt(statusInput);

            System.out.print("New timeout seconds [" + ep.getTimeoutSeconds() + "]: ");
            String timeoutInput = scanner.nextLine().trim();
            Integer newTimeout = timeoutInput.isEmpty() ? null : Integer.parseInt(timeoutInput);

            registryService.updateEndpoint(id, newName, newUrl, newStatus, newTimeout);
            registryService.getEndpointById(id).ifPresent(analyticsService::updateEndpoint);
            System.out.println(CLIFormatter.GREEN + "[SUCCESS] Endpoint " + id + " updated successfully." + CLIFormatter.RESET);
        } catch (NumberFormatException e) {
            System.out.println(CLIFormatter.RED + "Error: Status code and timeout must be valid integers." + CLIFormatter.RESET);
        } catch (Exception e) {
            System.out.println(CLIFormatter.RED + "Update failed: " + e.getMessage() + CLIFormatter.RESET);
        }
    }

    private void handleDeleteEndpoint() {
        CLIFormatter.printHeader("Delete Endpoint");
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        CLIFormatter.printEndpointTable(endpoints);

        if (endpoints.isEmpty()) return;

        System.out.print("\nEnter Endpoint ID to delete (e.g. EP-101): ");
        String id = scanner.nextLine().trim();

        Optional<Endpoint> ep = registryService.getEndpointById(id);
        if (ep.isEmpty()) {
            System.out.println(CLIFormatter.RED + "Endpoint with ID '" + id + "' not found." + CLIFormatter.RESET);
            return;
        }

        System.out.print(CLIFormatter.YELLOW + "Are you sure you want to delete '" + ep.get().getName() + "'? (y/N): " + CLIFormatter.RESET);
        String confirm = scanner.nextLine().trim();
        if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
            try {
                boolean deleted = registryService.deleteEndpoint(id);
                if (deleted) {
                    analyticsService.removeEndpoint(id);
                    System.out.println(CLIFormatter.GREEN + "[SUCCESS] Endpoint " + id + " deleted." + CLIFormatter.RESET);
                }
            } catch (IOException e) {
                System.out.println(CLIFormatter.RED + "Error deleting endpoint: " + e.getMessage() + CLIFormatter.RESET);
            }
        } else {
            System.out.println("Deletion cancelled.");
        }
    }

    private void handleRunHealthCheck() {
        CLIFormatter.printHeader("Concurrent Health Check Cycle");
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        if (endpoints.isEmpty()) {
            System.out.println(CLIFormatter.YELLOW + "No endpoints to check. Please add an endpoint first." + CLIFormatter.RESET);
            return;
        }

        System.out.println(CLIFormatter.CYAN + "Polling " + endpoints.size() + " endpoints asynchronously in parallel..." + CLIFormatter.RESET);
        long startTime = System.currentTimeMillis();

        List<HealthCheckResult> results = pollingEngine.checkAllConcurrently(endpoints);
        long totalDuration = System.currentTimeMillis() - startTime;

        analyticsService.recordBatch(results);

        CLIFormatter.printHealthCheckResults(results);
        System.out.println(CLIFormatter.GRAY + "Total cycle execution time: " + totalDuration + " ms (parallel concurrency achieved)" + CLIFormatter.RESET);
    }

    /**
     * Executes a single batch check without user interaction (used for headless testing / CLI flags).
     */
    public void runSingleBatchCheck() {
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        System.out.println("Running automated single batch check on " + endpoints.size() + " endpoints...");
        List<HealthCheckResult> results = pollingEngine.checkAllConcurrently(endpoints);
        analyticsService.recordBatch(results);
        CLIFormatter.printHealthCheckResults(results);
        CLIFormatter.printAnalyticsDashboard(analyticsService.getAllStats(), 
                analyticsService.getGlobalUptimePercentage(), 
                analyticsService.getTotalChecksCount(), 
                analyticsService.getTotalFailuresCount());
    }

    private void handleContinuousMonitoring() {
        CLIFormatter.printHeader("Continuous Real-Time Monitoring");
        List<Endpoint> endpoints = registryService.getAllEndpoints();
        if (endpoints.isEmpty()) {
            System.out.println(CLIFormatter.YELLOW + "No endpoints registered to monitor." + CLIFormatter.RESET);
            return;
        }

        System.out.print("Enter polling interval in seconds [default 5]: ");
        String intervalInput = scanner.nextLine().trim();
        int intervalSeconds = 5;
        try {
            if (!intervalInput.isEmpty()) {
                intervalSeconds = Math.max(1, Integer.parseInt(intervalInput));
            }
        } catch (NumberFormatException e) {
            System.out.println(CLIFormatter.YELLOW + "Invalid input, defaulting to 5 seconds." + CLIFormatter.RESET);
        }

        System.out.println(CLIFormatter.GREEN + "Starting continuous monitor (Interval: " + intervalSeconds + "s)." + CLIFormatter.RESET);
        System.out.println(CLIFormatter.YELLOW + "Press Enter at any time to return to Main Menu...\n" + CLIFormatter.RESET);

        // Run continuous polling in a background thread while main thread listens for Enter
        final int interval = intervalSeconds;
        final boolean[] keepRunning = { true };

        Thread monitorThread = new Thread(() -> {
            int cycle = 1;
            while (keepRunning[0]) {
                System.out.println(CLIFormatter.BLUE + "\n[Cycle #" + cycle + "] Polling at " + java.time.LocalTime.now().toString().substring(0, 8) + CLIFormatter.RESET);
                List<HealthCheckResult> results = pollingEngine.checkAllConcurrently(registryService.getAllEndpoints());
                analyticsService.recordBatch(results);
                CLIFormatter.printHealthCheckResults(results);

                cycle++;
                try {
                    for (int s = 0; s < interval * 10 && keepRunning[0]; s++) {
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        monitorThread.setDaemon(true);
        monitorThread.start();

        // Wait for user keypress (Enter) or stream termination
        readLineSafe();
        keepRunning[0] = false;
        monitorThread.interrupt();

        System.out.println(CLIFormatter.GREEN + "\nContinuous monitoring stopped. Returning to Main Menu." + CLIFormatter.RESET);
    }

    private void handleViewAnalytics() {
        CLIFormatter.printHeader("Analytics & SLA Report");
        CLIFormatter.printAnalyticsDashboard(analyticsService.getAllStats(),
                analyticsService.getGlobalUptimePercentage(),
                analyticsService.getTotalChecksCount(),
                analyticsService.getTotalFailuresCount());
    }

    private void handleViewIncidentLogs() {
        CLIFormatter.printHeader("Incident Event Logs");
        List<String> logs = fileHandler.readRecentLogs(25);
        if (logs.isEmpty()) {
            System.out.println(CLIFormatter.GREEN + "  No incidents or failure events recorded in logs/health_events.log." + CLIFormatter.RESET);
            return;
        }

        System.out.println(CLIFormatter.GRAY + "Displaying last " + logs.size() + " incident events from " + fileHandler.getIncidentLogFilePath() + ":" + CLIFormatter.RESET);
        System.out.println("--------------------------------------------------------------------------------");
        for (String line : logs) {
            if (line.contains("DOWN") || line.contains("ERROR")) {
                System.out.println(CLIFormatter.RED + line + CLIFormatter.RESET);
            } else if (line.contains("TIMEOUT")) {
                System.out.println(CLIFormatter.YELLOW + line + CLIFormatter.RESET);
            } else {
                System.out.println(line);
            }
        }
        System.out.println("--------------------------------------------------------------------------------");
    }

    private void handleReloadRegistry() {
        registryService.reload();
        System.out.println(CLIFormatter.GREEN + "[SUCCESS] Reloaded " + registryService.getAllEndpoints().size() + " endpoints from " + fileHandler.getEndpointsFilePath() + CLIFormatter.RESET);
    }

    private void shutdown() {
        pollingEngine.shutdown();
    }
}
