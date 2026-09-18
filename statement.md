# DevPulse: Server & API Health Monitoring CLI
## Problem Statement, Scope & Methodology Specification

### 1. Problem Statement
In modern microservices and cloud infrastructure environments, software engineers, DevOps practitioners, and system administrators manage dozens of distributed HTTP/REST APIs, web services, and internal endpoints. When service degradation or network outages occur, teams require immediate visibility into latency and availability without the resource footprint, complex configuration, or graphical dependencies of enterprise APM (Application Performance Monitoring) suites like Datadog, Dynatrace, or Grafana.

Existing lightweight tools (such as native `ping` or `curl`) are either restricted to ICMP layer-3 packets or lack built-in persistence, concurrent batch polling, real-time SLA metrics, and incident auditing. **DevPulse** directly bridges this gap by providing an autonomous, high-concurrency, terminal-based CLI application capable of probing multiple HTTP/HTTPS endpoints simultaneously, tracking response latency with sub-millisecond precision, computing SLA uptime percentages, and logging downtime events to local persistent storage.

---

### 2. Project Scope

#### In Scope:
- **Zero-Dependency Core**: Built exclusively on standard Java (JDK 11+) utilizing `java.net.http.HttpClient`, `CompletableFuture`, `java.nio.file`, and `java.time`.
- **Target Registry Management (CRUD)**:
  - Add, read, update, and delete monitored endpoints.
  - Persistent storage in standard CSV format (`data/endpoints.csv`).
- **Concurrent Polling Engine**:
  - Non-blocking parallel health checks using an asynchronous worker pool.
  - Granular latency measurement via high-resolution hardware timers (`System.nanoTime`).
  - Resilient network error handling (DNS failures, connection timeouts, HTTP 4xx/5xx status anomalies).
- **Analytics & SLA Computation**:
  - Live session calculation of SLA uptime percentage: `(Successful Checks / Total Checks) * 100`.
  - Latency analytics: minimum, maximum, and average response times per target.
- **Auditing & Incident Logging**:
  - Automatic event logging (`logs/health_events.log`) for downtime and status code deviations.
- **Interactive Terminal UI & Scriptable Interface**:
  - Interactive ASCII menu loop for system administrators.
  - Headless batch flag (`--check-once`) for automated CI/CD pipelines.

#### Out of Scope:
- Graphical user interfaces (GUI) such as JavaFX, Swing, or web browsers.
- Heavy external relational or NoSQL database servers.
- Third-party build managers or external runtime dependencies.

---

### 3. Target Users & Stakeholders
1. **System Administrators & Site Reliability Engineers (SREs)**: Requiring a lightweight diagnostic CLI to monitor endpoints directly from SSH terminal sessions.
2. **Backend & API Developers**: Seeking an instant local tool to verify endpoint latency and HTTP response status across microservice clusters during integration testing.
3. **Computer Science Evaluators & Academics**: Requiring a clean reference implementation of Layered Architecture, Concurrency (`ExecutorService` / `CompletableFuture`), File I/O persistence, and Object-Oriented Design patterns in pure Java.

---

### 4. High-Level Features
- **Concurrent Polling Engine**: Parallel asynchronous probing using `CompletableFuture` and `java.net.http.HttpClient` with sub-millisecond hardware timers (`System.nanoTime`).
- **Endpoint Registry (CRUD)**: Complete lifecycle management of monitored endpoints with persistent RFC-4180 compliant CSV storage and atomic file swaps.
- **SLA & Real-Time Analytics**: Automatic calculation of SLA uptime percentage, min/avg/max latency distribution, and session summaries.
- **Incident Auditing & Alert Logging**: Automatic incident event logging (`logs/health_events.log`) for downtime, SLA breaches, and HTTP status code deviations.
- **Interactive Terminal Dashboard & Automation**: 10-option interactive menu, continuous live monitoring mode, and headless `--check-once` automated smoke testing.

---

### 5. Approach & Methodology
The project follows an **Iterative Object-Oriented Methodology** adhering to Clean Code and SOLID design principles:

1. **Separation of Concerns (Layered Architecture)**:
   - **Presentation Layer (`com.devpulse.main`, `com.devpulse.utils`)**: Manages console interaction, ASCII tables, and ANSI colorized outputs.
   - **Service Layer (`com.devpulse.service`)**: Encapsulates business logic, asynchronous concurrency orchestration, and SLA aggregation.
   - **Data Access Layer (`com.devpulse.repository`)**: Governs file reading, writing, and atomic serialization without third-party libraries.
   - **Model Layer (`com.devpulse.model`)**: Encapsulates entity state and behavioral invariants.

2. **Concurrency & Performance Strategy**:
   - Rather than sequential iterative HTTP queries (which scale linearly with $O(N)$ network latency), DevPulse dispatches parallel asynchronous worker tasks via Java's `CompletableFuture` and a cached thread pool. Total check duration is bounded by $O(\max(t_i))$ rather than $O(\sum t_i)$.

3. **Resilience & Fault Tolerance**:
   - Comprehensive multi-tiered exception handling safeguards against network dropouts, DNS resolution errors, and socket timeouts, ensuring uninterrupted CLI operation.
