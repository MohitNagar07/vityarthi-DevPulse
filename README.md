# DevPulse: Server & API Health Monitoring CLI

> **High-performance, lightweight, pure Java command-line interface for concurrent server and API health tracking, latency benchmarking, and SLA analytics.**

[![Java Version](https://img.shields.io/badge/Java-11%2B%20%28Tested%20on%2025%20LTS%29-orange.svg)](#prerequisites)
[![Architecture](https://img.shields.io/badge/Architecture-3--Tier%20Layered-blue.svg)](docs/ARCHITECTURE.md)
[![Dependencies](https://img.shields.io/badge/Dependencies-Zero%20%28Standard%20Library%29-brightgreen.svg)](#key-features)
[![CLI](https://img.shields.io/badge/Interface-100%25%20Pure%20CLI-success.svg)](#interactive-menu-options)

---

## Overview

Modern software applications rely heavily on distributed web services and microservice architectures. When latency spikes or servers experience downtime, developers and operations teams require immediate diagnosis. **DevPulse** is an enterprise-grade, lightweight CLI monitoring solution engineered in **pure Java** with zero external dependencies.

DevPulse concurrently checks multiple HTTP and HTTPS targets using asynchronous network threads, records response times with millisecond precision, computes live uptime SLA percentages, and persistently audits incidents to local storage.

---

## Key Features

- ⚡ **Asynchronous Concurrency**: Built with Java's standard `CompletableFuture` and `java.net.http.HttpClient` to poll dozens of endpoints simultaneously, ensuring cycle execution time equals only the slowest single request.
- 🎯 **Sub-Millisecond Latency Benchmarking**: Uses `System.nanoTime()` timers to track min, average, and max latency.
- 📊 **Real-Time SLA & Analytics Dashboard**: Computes session uptime percentages, failure counts, and performance breakdowns per endpoint.
- 🚨 **Automated Incident Logging**: Records failed requests (status code mismatches, timeouts, DNS/connection drops) to `logs/health_events.log`.
- 💾 **File-Based Persistence**: Saves and manages endpoint configurations in standard CSV (`data/endpoints.csv`) with automatic seeding on first run.
- 🎨 **Modern CLI Presentation**: ANSI color-coded statuses, ASCII border layouts, responsive table formatting, and clear visual indicators (`[OK] UP`, `[FAIL] DOWN`, `[WARN] TIMEOUT`, `[ERR] ERROR`).
- 🔄 **Continuous Monitor Mode**: Real-time polling mode with configurable refresh intervals and background execution.

---

## Architecture & Package Structure

DevPulse follows strict object-oriented design and layered separation of concerns:

```
Vityarthi 2/
├── src/
│   └── com/
│       └── devpulse/
│           ├── main/
│           │   └── DevPulseApp.java             # Interactive CLI menu loop & entry point
│           ├── model/
│           │   ├── Endpoint.java                # Monitored target entity with CSV serialization
│           │   ├── HealthCheckResult.java       # Ping result, timing, and log formatting
│           │   └── ServiceStatus.java           # Enum: UP, DOWN, TIMEOUT, ERROR with ANSI colors
│           ├── repository/
│           │   └── FileHandler.java             # File I/O for data/endpoints.csv & logs/health_events.log
│           ├── service/
│           │   ├── RegistryService.java         # CRUD business logic and URL validation
│           │   ├── PollingEngine.java           # Asynchronous multithreaded HTTP polling engine
│           │   └── AnalyticsService.java        # SLA uptime %, latency stats, and incident dispatch
│           └── utils/
│               └── CLIFormatter.java            # ASCII banners, dynamic tables, and color helpers
├── data/
│   └── endpoints.csv                            # Persistent endpoint registry
├── logs/
│   └── health_events.log                        # Audited downtime and incident history
├── docs/
│   └── ARCHITECTURE.md                          # UML, Class, Use-Case & Sequence Diagrams
├── statement.md                                 # Academic problem statement and methodology
├── run.bat                                      # One-click Windows CMD compilation and run script
└── run.ps1                                      # One-click PowerShell compilation and run script
```

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 11 or higher (OpenJDK, Oracle JDK, or Temurin).
- No external libraries, Gradle, or Maven installations needed!

---

## Quick Start & Running Instructions

### Option 1: Using Windows Batch Script (`run.bat`)
Double-click `run.bat` or run from Command Prompt:
```cmd
run.bat
```

### Option 2: Using PowerShell Script (`run.ps1`)
```powershell
.\run.ps1
```

### Option 3: Manual Compilation & Execution
From the root project directory:
```powershell
# 1. Compile all Java source files into bin directory
javac -d bin (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

# 2. Run the interactive CLI application
java -cp bin com.devpulse.main.DevPulseApp
```

### Option 4: Headless Automated Smoke Test (CI/CD Mode)
To run a one-shot automated batch health check across all endpoints without entering interactive menu mode:
```powershell
java -cp bin com.devpulse.main.DevPulseApp --check-once
```

---

## Interactive Menu Options

When launched, DevPulse presents an interactive command center:

```
================================================================================
  ____             ____        _            ____ _     ___ 
 |  _ \  _____   _|  _ \ _   _| |___  ___  / ___| |   |_ _|
 | | | |/ _ \ \ / / |_) | | | | / __|/ _ \| |   | |    | | 
 | |_| |  __/ \ V /|  __/| |_| | \__ \  __/| |___| |___ | | 
 |____/ \___|  \_/ |_|    \__,_|_|___/\___(_)____|_____|___|
   >> High-Performance Server & API Health Monitoring CLI <<
       Concurrent Engine | Real-Time Latency | SLA Analytics
================================================================================

+------------------------ MAIN MENU ------------------------+
  1. List Registered Endpoints
  2. Add New Endpoint
  3. Update Existing Endpoint
  4. Delete Endpoint
  5. Run Health Check (Concurrent Ping All)
  6. Start Continuous Real-Time Monitor
  7. View Analytics & SLA Summary Report
  8. View Incident Logs (health_events.log)
  9. Reload Configuration from Disk
  0. Exit Application
+-----------------------------------------------------------+

Select an option [0-9]:
```

---

## Sample Terminal Output

### 1. Concurrent Health Check Results (Option 5)
```
+----------+------------------------+--------------+--------+------------+----------+--------------------------+
| ID       | Service Name           | Status       | Code   | Latency    | Time     | Diagnostics              |
+----------+------------------------+--------------+--------+------------+----------+--------------------------+
| EP-101   | Cloudflare Trace       | [OK] UP      | 200    | 726 ms     | 09:43:57 | Healthy (HTTP 200)       |
| EP-102   | Google Public DNS      | [OK] UP      | 200    | 726 ms     | 09:43:57 | Healthy (HTTP 200)       |
| EP-103   | JSONPlaceholder API    | [OK] UP      | 200    | 909 ms     | 09:43:57 | Healthy (HTTP 200)       |
| EP-104   | HTTPBin Success (200)  | [OK] UP      | 200    | 1437 ms    | 09:43:57 | Healthy (HTTP 200)       |
| EP-105   | HTTPBin Simulated Fail | [FAIL] DOWN  | 500    | 1437 ms    | 09:43:57 | Unexpected status: ex... |
+----------+------------------------+--------------+--------+------------+----------+--------------------------+
```

### 2. SLA Analytics & Latency Summary (Option 7)
```
============================= GLOBAL HEALTH SUMMARY =============================
  Total Invocations: 5      | Failures: 1      | Overall Session SLA: 80.00%
=================================================================================
+----------+------------------------+------------+--------+--------+----------+----------+----------+
| ID       | Service Name           | Uptime SLA | Pings  | Fails  | Min Lat  | Avg Lat  | Max Lat  |
+----------+------------------------+------------+--------+--------+----------+----------+----------+
| EP-101   | Cloudflare Trace       | 100.00%    | 1      | 0      | 726ms    | 726.0ms  | 726ms    |
| EP-102   | Google Public DNS      | 100.00%    | 1      | 0      | 726ms    | 726.0ms  | 726ms    |
| EP-103   | JSONPlaceholder API    | 100.00%    | 1      | 0      | 909ms    | 909.0ms  | 909ms    |
| EP-104   | HTTPBin Success (200)  | 100.00%    | 1      | 0      | 1437ms   | 1437.0ms | 1437ms   |
| EP-105   | HTTPBin Simulated Fail | 0.00%      | 1      | 1      | 1437ms   | 1437.0ms | 1437ms   |
+----------+------------------------+------------+--------+--------+----------+----------+----------+
```

### 3. Incident Audit Log (`logs/health_events.log`) (Option 8)
```
[2026-09-18 09:43:57] [STATUS: DOWN] [ID: EP-105] [NAME: HTTPBin Simulated Failure (500)] [URL: https://httpbin.org/status/500] [CODE: 500] [LATENCY: 1437ms] - Unexpected status: expected 200, got 500
```

---

## License & Academic Compliance
Developed strictly following object-oriented software engineering principles for academic evaluation and real-world developer productivity. All source code contains comprehensive JavaDoc comments.
