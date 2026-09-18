package com.devpulse.test;

import com.devpulse.model.Endpoint;
import com.devpulse.model.HealthCheckResult;
import com.devpulse.model.ServiceStatus;
import com.devpulse.repository.FileHandler;
import com.devpulse.service.AnalyticsService;
import com.devpulse.service.PollingEngine;
import com.devpulse.service.RegistryService;
import com.devpulse.utils.CLIFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Automated comprehensive functional, unit, and edge-case verification test suite for DevPulse.
 * Tests CRUD operations, file persistence, RFC-4180 CSV parsing, memory-bounded log tailing,
 * ANSI visual alignment, concurrent polling, and analytics computation.
 */
public class DevPulseTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  Running DevPulse Comprehensive Test Suite");
        System.out.println("=================================================");

        int passed = 0;
        int failed = 0;

        TestCase[] tests = new TestCase[] {
            new TestCase("testEndpointModelAndCsvRFC4180", DevPulseTest::testEndpointModelAndCsvRFC4180),
            new TestCase("testUrlValidationAndBoundaries", DevPulseTest::testUrlValidationAndBoundaries),
            new TestCase("testFilePersistenceAndAtomicWrite", DevPulseTest::testFilePersistenceAndAtomicWrite),
            new TestCase("testMemoryBoundedLogReading", DevPulseTest::testMemoryBoundedLogReading),
            new TestCase("testAnalyticsAndSlaCalculation", DevPulseTest::testAnalyticsAndSlaCalculation),
            new TestCase("testAnalyticsEndpointSync", DevPulseTest::testAnalyticsEndpointSync),
            new TestCase("testCliFormatterVisualPadding", DevPulseTest::testCliFormatterVisualPadding),
            new TestCase("testPollingEngineConcurrency", DevPulseTest::testPollingEngineConcurrency)
        };

        for (TestCase tc : tests) {
            try {
                tc.runnable.run();
                System.out.println("[PASS] " + tc.name);
                passed++;
            } catch (Throwable t) {
                System.err.println("[FAIL] " + tc.name + ": " + t.getMessage());
                t.printStackTrace();
                failed++;
            }
        }

        System.out.println("=================================================");
        System.out.printf("  Tests Completed: %d Passed, %d Failed\n", passed, failed);
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Exception;
    }

    static class TestCase {
        final String name;
        final TestRunnable runnable;

        TestCase(String name, TestRunnable runnable) {
            this.name = name;
            this.runnable = runnable;
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError("Condition failed: " + message);
    }

    private static void testEndpointModelAndCsvRFC4180() {
        // Standard endpoint
        Endpoint ep = new Endpoint("EP-999", "Test API", "https://api.test.com", 200, 3);
        String csv = ep.toCsv();
        Endpoint parsed = Endpoint.fromCsv(csv);
        assertTrue(parsed != null, "Parsed endpoint should not be null");
        assertEquals("EP-999", parsed.getId(), "Endpoint ID should match");
        assertEquals("Test API", parsed.getName(), "Endpoint Name should match");

        // Edge Case: Commas and quotes in name
        Endpoint complexEp = new Endpoint("EP-100", "Microservice, \"Auth\" (East)", "https://auth.internal.net", 204, 10);
        String complexCsv = complexEp.toCsv();
        Endpoint parsedComplex = Endpoint.fromCsv(complexCsv);
        assertTrue(parsedComplex != null, "Complex CSV should parse");
        assertEquals("Microservice, \"Auth\" (East)", parsedComplex.getName(), "Quoted name with comma should match");
        assertEquals(204, parsedComplex.getExpectedStatusCode(), "Status code should match");
        assertEquals(10, parsedComplex.getTimeoutSeconds(), "Timeout should match");

        // Corrupted / malformed input lines
        assertTrue(Endpoint.fromCsv(null) == null, "Null line returns null");
        assertTrue(Endpoint.fromCsv("") == null, "Empty line returns null");
        assertTrue(Endpoint.fromCsv("# comment line") == null, "Comment line returns null");
        assertTrue(Endpoint.fromCsv("EP-1,Only,Three") == null, "Underflow columns returns null");
        assertTrue(Endpoint.fromCsv("EP-1,Name,https://test.com,not_a_number,5") == null, "Invalid status number returns null");
    }

    private static void testUrlValidationAndBoundaries() throws IOException {
        // Valid URLs
        assertTrue(RegistryService.isValidUrl("http://localhost:8080"), "localhost is valid");
        assertTrue(RegistryService.isValidUrl("https://127.0.0.1:3000/api"), "IP address is valid");
        assertTrue(RegistryService.isValidUrl("https://api.example.com/v1/health?token=xyz#section"), "Query and hash valid");

        // Invalid URLs
        assertTrue(!RegistryService.isValidUrl("ftp://example.com"), "FTP is rejected");
        assertTrue(!RegistryService.isValidUrl("http://"), "Missing host is rejected");
        assertTrue(!RegistryService.isValidUrl("https://"), "Missing host is rejected");
        assertTrue(!RegistryService.isValidUrl("not-a-url"), "Raw text is rejected");
        assertTrue(!RegistryService.isValidUrl("https://example .com"), "Space in domain rejected");

        // Test bounds validation in RegistryService
        FileHandler handler = new FileHandler(Files.createTempFile("ep_val", ".csv"), Files.createTempFile("log_val", ".log"));
        RegistryService registry = new RegistryService(handler);

        // Blank name
        try {
            registry.addEndpoint(" ", "https://api.com", 200, 5);
            throw new AssertionError("Should reject empty name");
        } catch (IllegalArgumentException expected) {}

        // Invalid status code
        try {
            registry.addEndpoint("Test", "https://api.com", 999, 5);
            throw new AssertionError("Should reject status code > 599");
        } catch (IllegalArgumentException expected) {}

        // Invalid timeout
        try {
            registry.addEndpoint("Test", "https://api.com", 200, 0);
            throw new AssertionError("Should reject timeout < 1");
        } catch (IllegalArgumentException expected) {}

        try {
            registry.addEndpoint("Test", "https://api.com", 200, 100);
            throw new AssertionError("Should reject timeout > 60");
        } catch (IllegalArgumentException expected) {}
    }

    private static void testFilePersistenceAndAtomicWrite() throws IOException {
        Path tempCsv = Files.createTempFile("devpulse_test_ep", ".csv");
        Path tempLog = Files.createTempFile("devpulse_test_log", ".log");
        tempCsv.toFile().deleteOnExit();
        tempLog.toFile().deleteOnExit();

        FileHandler handler = new FileHandler(tempCsv, tempLog);
        RegistryService registry = new RegistryService(handler);

        // Add
        Endpoint ep1 = registry.addEndpoint("Internal API", "https://1.1.1.1/cdn-cgi/trace", 200, 4);
        assertEquals(1, registry.getAllEndpoints().size(), "Registry size should be 1 after add");

        // Reload from disk
        RegistryService registryReloaded = new RegistryService(handler);
        assertEquals(1, registryReloaded.getAllEndpoints().size(), "Reloaded registry size should be 1");
        assertEquals(ep1.getId(), registryReloaded.getAllEndpoints().get(0).getId(), "Endpoint ID should match after reload");

        // Update
        boolean updated = registry.updateEndpoint(ep1.getId(), "Renamed API", null, 204, null);
        assertTrue(updated, "Update should return true");
        Endpoint updatedEp = registry.getEndpointById(ep1.getId()).orElseThrow();
        assertEquals("Renamed API", updatedEp.getName(), "Name should be updated");
        assertEquals(204, updatedEp.getExpectedStatusCode(), "Status code should be updated");

        // Delete
        boolean deleted = registry.deleteEndpoint(ep1.getId());
        assertTrue(deleted, "Delete should return true");
        assertEquals(0, registry.getAllEndpoints().size(), "Registry size should be 0 after delete");
    }

    private static void testMemoryBoundedLogReading() throws IOException {
        Path tempLog = Files.createTempFile("devpulse_tail_test", ".log");
        tempLog.toFile().deleteOnExit();
        FileHandler handler = new FileHandler(Files.createTempFile("dummy", ".csv"), tempLog);

        // Write 50 incident events
        Endpoint ep = new Endpoint("EP-1", "Test", "https://example.com", 200, 5);
        for (int i = 1; i <= 50; i++) {
            HealthCheckResult res = new HealthCheckResult(ep, null, 500, 100, ServiceStatus.DOWN, "Incident #" + i);
            handler.logIncident(res);
        }

        // Request last 10 lines
        List<String> last10 = handler.readRecentLogs(10);
        assertEquals(10, last10.size(), "Should return exactly 10 lines");
        assertTrue(last10.get(0).contains("Incident #41"), "First line in tail should be Incident #41");
        assertTrue(last10.get(9).contains("Incident #50"), "Last line in tail should be Incident #50");
    }

    private static void testAnalyticsAndSlaCalculation() throws IOException {
        Path tempLog = Files.createTempFile("devpulse_test_analytics", ".log");
        tempLog.toFile().deleteOnExit();
        FileHandler handler = new FileHandler(Files.createTempFile("dummy", ".csv"), tempLog);
        AnalyticsService analytics = new AnalyticsService(handler);

        Endpoint ep = new Endpoint("EP-1", "Test Target", "https://example.com", 200, 5);

        HealthCheckResult r1 = new HealthCheckResult(ep, null, 200, 100, ServiceStatus.UP, "OK");
        HealthCheckResult r2 = new HealthCheckResult(ep, null, 200, 150, ServiceStatus.UP, "OK");
        HealthCheckResult r3 = new HealthCheckResult(ep, null, 500, 200, ServiceStatus.DOWN, "Server Error");
        HealthCheckResult r4 = new HealthCheckResult(ep, null, 200, 50, ServiceStatus.UP, "OK");

        analytics.recordResult(r1);
        analytics.recordResult(r2);
        analytics.recordResult(r3);
        analytics.recordResult(r4);

        assertEquals(4, analytics.getTotalChecksCount(), "Total checks should be 4");
        assertEquals(1, analytics.getTotalFailuresCount(), "Total failures should be 1");
        assertEquals(75.0, analytics.getGlobalUptimePercentage(), "Global SLA should be 75.0%");

        AnalyticsService.EndpointStats stats = analytics.getStats("EP-1");
        assertTrue(stats != null, "EndpointStats should exist");
        assertEquals(50L, stats.getMinLatencyMs(), "Min latency should be 50ms");
        assertEquals(200L, stats.getMaxLatencyMs(), "Max latency should be 200ms");
        assertEquals(125.0, stats.getAverageLatencyMs(), "Average latency should be 125ms");
        assertEquals(75.0, stats.getUptimePercentage(), "Endpoint SLA should be 75.0%");
    }

    private static void testAnalyticsEndpointSync() throws IOException {
        FileHandler handler = new FileHandler(Files.createTempFile("ep_sync", ".csv"), Files.createTempFile("log_sync", ".log"));
        AnalyticsService analytics = new AnalyticsService(handler);

        Endpoint ep = new Endpoint("EP-101", "Old Name", "https://old.com", 200, 5);
        analytics.recordResult(new HealthCheckResult(ep, null, 200, 80, ServiceStatus.UP, "OK"));

        assertEquals("Old Name", analytics.getStats("EP-101").getEndpoint().getName(), "Stats should have initial name");

        // Update endpoint
        ep.setName("Brand New Name");
        analytics.updateEndpoint(ep);
        assertEquals("Brand New Name", analytics.getStats("EP-101").getEndpoint().getName(), "Stats should reflect updated name");

        // Delete endpoint
        analytics.removeEndpoint("EP-101");
        assertTrue(analytics.getStats("EP-101") == null, "Stats should be removed after deletion");
    }

    private static void testCliFormatterVisualPadding() {
        String coloredText = "\u001B[32m[OK] UP\u001B[0m";
        String stripped = CLIFormatter.stripAnsi(coloredText);
        assertEquals("[OK] UP", stripped, "Stripped ANSI text should match raw label");

        String padded = CLIFormatter.padRight(coloredText, 12);
        // Stripped padded string should have exactly 12 characters length
        assertEquals(12, CLIFormatter.stripAnsi(padded).length(), "Visual width of padded text should be exactly 12");
    }

    private static void testPollingEngineConcurrency() {
        PollingEngine engine = new PollingEngine();
        try {
            Endpoint ep1 = new Endpoint("T-1", "Cloudflare Trace", "https://1.1.1.1/cdn-cgi/trace", 200, 5);
            Endpoint ep2 = new Endpoint("T-2", "Google DNS", "https://dns.google", 200, 5);

            long start = System.currentTimeMillis();
            List<HealthCheckResult> results = engine.checkAllConcurrently(List.of(ep1, ep2));
            long duration = System.currentTimeMillis() - start;

            assertEquals(2, results.size(), "Should have 2 results");
            assertTrue(duration < 5000, "Parallel check should complete within 5 seconds");
            for (HealthCheckResult res : results) {
                assertTrue(res.getLatencyMs() >= 0, "Latency should be non-negative");
            }
        } finally {
            engine.shutdown();
        }
    }
}
