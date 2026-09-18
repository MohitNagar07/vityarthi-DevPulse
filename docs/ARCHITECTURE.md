# DevPulse Architecture & Design Specification

DevPulse is engineered according to a classic 3-Tier Layered Architecture with decoupled responsibilities across Presentation, Service, Model, and Persistence layers.

---

## 1. System Architecture Diagram

```mermaid
graph TD
    User([System Admin / Developer]) <-->|Terminal Input / ANSI Output| CLI[DevPulseApp CLI Entry Point]

    subgraph Presentation Layer
        CLI
        Formatter[CLIFormatter: ASCII Tables & ANSI Colors]
    end

    subgraph Service Layer
        RegService[RegistryService: CRUD Logic]
        PollEngine[PollingEngine: CompletableFuture & HttpClient]
        Analytics[AnalyticsService: SLA & Latency Metrics]
    end

    subgraph Data Access Layer
        FileRepo[FileHandler: CSV & Log I/O]
    end

    subgraph Storage
        CSVStore[("data/endpoints.csv")]
        LogStore[("logs/health_events.log")]
    end

    subgraph Target Network
        HTTP1[Endpoint: Cloudflare Trace]
        HTTP2[Endpoint: Google DNS]
        HTTP3[Endpoint: REST Microservice]
    end

    CLI --> RegService
    CLI --> PollEngine
    CLI --> Analytics
    CLI --> Formatter

    RegService --> FileRepo
    Analytics --> FileRepo
    FileRepo --> CSVStore
    FileRepo --> LogStore

    PollEngine -.->|Concurrent Async Ping| HTTP1
    PollEngine -.->|Concurrent Async Ping| HTTP2
    PollEngine -.->|Concurrent Async Ping| HTTP3
    PollEngine --> Analytics
```

---

## 2. Use Case Diagram

```mermaid
graph LR
    Actor((Dev / Admin))

    subgraph DevPulse System
        UC1[List Monitored Endpoints]
        UC2[Add New Endpoint]
        UC3[Update / Delete Endpoint]
        UC4[Trigger Parallel Health Check]
        UC5[Start Continuous Live Monitor]
        UC6[Inspect SLA & Latency Analytics]
        UC7[View Incident Event Logs]
    end

    Actor --> UC1
    Actor --> UC2
    Actor --> UC3
    Actor --> UC4
    Actor --> UC5
    Actor --> UC6
    Actor --> UC7
```

---

## 3. Class Diagram

```mermaid
classDiagram
    class Endpoint {
        -String id
        -String name
        -String url
        -int expectedStatusCode
        -int timeoutSeconds
        +toCsv() String
        +fromCsv(String) Endpoint
        +getId() String
        +getUrl() String
    }

    class ServiceStatus {
        <<enumeration>>
        UP
        DOWN
        TIMEOUT
        ERROR
        +getLabel() String
        +getColoredLabel() String
        +isHealthy() boolean
    }

    class HealthCheckResult {
        -Endpoint endpoint
        -LocalDateTime timestamp
        -int statusCode
        -long latencyMs
        -ServiceStatus status
        -String message
        +isUp() boolean
        +toLogEntry() String
    }

    class FileHandler {
        -Path endpointsFilePath
        -Path incidentLogFilePath
        +loadEndpoints() List~Endpoint~
        +saveEndpoints(List~Endpoint~) void
        +logIncident(HealthCheckResult) void
        +readRecentLogs(int) List~String~
    }

    class RegistryService {
        -FileHandler fileHandler
        -List~Endpoint~ endpointList
        +addEndpoint(...) Endpoint
        +getAllEndpoints() List~Endpoint~
        +getEndpointById(String) Optional~Endpoint~
        +updateEndpoint(...) boolean
        +deleteEndpoint(String) boolean
        +reload() void
    }

    class PollingEngine {
        -HttpClient httpClient
        -ExecutorService executorService
        +checkEndpoint(Endpoint) HealthCheckResult
        +checkAllConcurrently(List~Endpoint~) List~HealthCheckResult~
        +shutdown() void
    }

    class AnalyticsService {
        -FileHandler fileHandler
        -Map~String, EndpointStats~ statsMap
        +recordBatch(List~HealthCheckResult~) void
        +recordResult(HealthCheckResult) void
        +getGlobalUptimePercentage() double
        +getAllStats() List~EndpointStats~
    }

    class CLIFormatter {
        <<utility>>
        +printBanner() void
        +printEndpointTable(...) void
        +printHealthCheckResults(...) void
        +printAnalyticsDashboard(...) void
        +formatLatency(long) String
        +formatSla(double) String
    }

    class DevPulseApp {
        -RegistryService registryService
        -PollingEngine pollingEngine
        -AnalyticsService analyticsService
        -FileHandler fileHandler
        +main(String[]) void
        +run() void
    }

    DevPulseApp --> RegistryService
    DevPulseApp --> PollingEngine
    DevPulseApp --> AnalyticsService
    DevPulseApp --> CLIFormatter
    RegistryService --> FileHandler
    AnalyticsService --> FileHandler
    PollingEngine ..> HealthCheckResult
    HealthCheckResult --> Endpoint
    HealthCheckResult --> ServiceStatus
```

---

## 4. Sequence Diagram (Concurrent Health Check Polling Cycle)

```mermaid
sequenceDiagram
    autonumber
    actor Admin as System Admin
    participant App as DevPulseApp (CLI)
    participant Reg as RegistryService
    participant Poll as PollingEngine
    participant HTTP as java.net.http.HttpClient
    participant Remote as External APIs / Web Endpoints
    participant Analytics as AnalyticsService
    participant Disk as FileHandler (Storage)

    Admin->>App: Select Option 5 (Run Health Check)
    App->>Reg: getAllEndpoints()
    Reg-->>App: List<Endpoint>
    App->>Poll: checkAllConcurrently(endpoints)
    
    par For Each Endpoint in Parallel
        Poll->>HTTP: sendAsync(HttpRequest, discardingBody)
        HTTP->>Remote: GET request with timeout threshold
        Remote-->>HTTP: HTTP Status Code & Headers
        HTTP-->>Poll: HttpResponse<Void> (Time measured: nanoTime)
    end

    Poll-->>App: List<HealthCheckResult>
    App->>Analytics: recordBatch(results)
    
    alt If Any Endpoint is DOWN or FAILING
        Analytics->>Disk: logIncident(HealthCheckResult)
        Disk->>Disk: Append to logs/health_events.log
    end

    Analytics-->>App: Metrics recorded
    App->>Admin: Render colorized ASCII results & latency table
```
