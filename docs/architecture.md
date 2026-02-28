# DGFacade Architecture

**Version:** 1.6.1
**Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved.**

## Overview

DGFacade is a configuration-driven data gateway facade system designed for high-performance,
multi-channel request handling. It processes millions of concurrent requests through a unified
handler model backed by Apache Pekko actors.

## Module Structure

```
dgfacade/
├── common/     - Shared models (DGRequest, DGResponse, HandlerState), exceptions, utilities
├── messaging/  - Abstract pub/sub with Kafka, ActiveMQ, RabbitMQ, IBM MQ, FileSystem, SQL
├── server/     - Pekko actor execution engine, handler lifecycle, ingestion, clustering
├── web/        - Spring Boot application, REST API, WebSocket, admin UI, monitoring
└── docs/       - Project documentation
```

## Request Flow

1. Request arrives via REST API, WebSocket, Kafka ingester, ActiveMQ ingester, or FileSystem ingester
2. Request is normalized to a `DGRequest` object with fields: api_key, request_type, payload, request_id, ttl_minutes
3. `ExecutionEngine` validates the API key and resolves the user
4. Handler configuration is looked up (user-specific override → default)
5. If clustering is enabled and handler not available locally, request is forwarded to a peer node
6. `HandlerSupervisor` spawns a `HandlerActor` child
7. `HandlerActor` instantiates the handler class and calls `construct(config)`
8. Actor schedules a TTL timeout message
9. Actor calls `execute(request)` or `executeStreaming(request, updateSink)`
10. On completion, timeout, or failure: `stop()` → `cleanup()` → actor terminates
11. `HandlerState` is logged to the `CircularBuffer` and handler-executions.log
12. Response is sent back via the originating channel or `delivery_destination`

## Actor Hierarchy

```
ActorSystem("dgfacade")
└── HandlerSupervisor
    ├── HandlerActor (handler-abc123)
    ├── HandlerActor (handler-def456)
    └── ... (millions of concurrent actors)
```

## Ingestion Architecture (v1.5.0+)

```
┌─────────────────────────────────────────────────┐
│              IngestionService                     │
│  ┌───────────────┐  ┌──────────────────┐         │
│  │ KafkaRequest   │  │ ActiveMQRequest   │        │
│  │ Ingester       │  │ Ingester          │        │
│  └───────┬───────┘  └───────┬──────────┘         │
│          │                   │                     │
│  ┌───────┴───────────────────┴──────────┐         │
│  │       AbstractRequestIngester         │        │
│  │  processMessage() → validate → submit │        │
│  └───────────────────┬──────────────────┘         │
│                      │                             │
│              ExecutionEngine.submit()              │
└─────────────────────────────────────────────────┘

Resolution Chain: ingester config → input_channel → broker
```

Each ingester resolves its configuration through a 3-layer chain:
the ingester config references an input channel, which references a broker,
yielding connection details + destinations + ingester-level overrides.

## Clustering Architecture (v1.4.0+)

Nodes discover each other via HTTP seed nodes, exchange heartbeats at configurable intervals,
and can forward handler execution to remote nodes when the local node lacks a handler.
Node roles: BOTH (default), GATEWAY (receive only), EXECUTOR (process only).

## Health & Monitoring (v1.6.0+)

All monitoring endpoints are public (no authentication required):
- `/ping` — Simple text response for load balancers
- `/api/ping` — JSON with version, uptime
- `/health` — Full health dashboard with JVM metrics, component status
- `/api/health` — JSON health for automated monitoring
- `/api/v1/metrics/snapshot` — Current metrics snapshot (heap, requests, errors, latency) for sparkline charts
- `/monitoring/logs` — Live log viewer with SSE streaming

The dashboard includes real-time sparkline charts that poll `/api/v1/metrics/snapshot` every 5 seconds,
visualizing request throughput, error rate, JVM heap usage, and average handler latency over time.
For comprehensive metrics with percentile histograms, use the Prometheus endpoint at `/actuator/prometheus`.

## Backpressure Model

Subscribers maintain an internal bounded queue. When the queue depth exceeds the configured
limit, the subscriber signals backpressure to the broker:
- **Kafka**: Consumer pauses partition assignment
- **ActiveMQ**: JMS session stops message delivery
- **FileSystem**: Polling interval increases
- **SQL**: Query fetch pauses

Messages remain safely on the broker until the subscriber has capacity.

## Security Model

- **API Layer**: Stateless, API-key authentication per request
- **Web UI**: Session-based with form login
- **Roles**: ADMIN (full access), USER (dashboard and monitoring only)
- **API Keys**: Per-user, with allowed request types and rate limiting
- **Public Endpoints**: /ping, /health, /monitoring/*, /about, /help, /api/ping, /api/health
- **Admin Endpoints**: /admin/* requires ADMIN role

## Startup Sequence

The system initializes in 9 phases:
1. Configuration Loading
2. External JAR Loading
3. Handler Registration
4. User & API Key Loading
5. Broker Configuration Loading
6. Channel Configuration Loading
7. Execution Engine Initialization
8. Cluster Formation
9. Ingester Startup

## Technology Stack

- Java 17, Spring Boot 3.2.5, Apache Pekko 1.0.2
- Kafka 3.7, ActiveMQ 6.1, Jackson 2.17
- Python 3 with Py4J for cross-language handler execution
- Thymeleaf, Bootstrap 5, Font Awesome 6
- Prometheus/Micrometer for metrics, Grafana for dashboards

## Python Handler Support (v1.6.1)

DGFacade supports writing handlers in Python using the same start/execute/stop contract as Java handlers.

### Architecture

```
 Java (Spring Boot)              Python (Worker Pool)
┌─────────────────┐            ┌────────────────────┐
│  DGHandlerPython │───JSON───►│  dgfacade_worker.py │
│  (Bridge)        │◄──JSON────│  ├─ dg_handler.py   │
└─────────────────┘     TCP    │  └─ handlers/       │
                               │     ├─ echo_handler  │
                               │     └─ transform_hdl │
                               └────────────────────┘
```

### How It Works

1. `DGHandlerPython` is a Java bridge handler that implements `DGHandler`
2. Python handlers are registered in standard handler configs (`config/handlers/default.json`, `admin.json`) with `"is_python": true` — they are conceptually identical to Java handlers
3. On `execute()`, the bridge reads `python_module` and `python_class` from the handler config, serializes the `DGRequest` to JSON, and dispatches to a Python worker via TCP (Py4J)
4. The Python worker imports the specified handler class, calls its `compute()` method, and returns the result as JSON
5. `PythonWorkerManager` manages N worker processes with round-robin dispatch, health checks, and auto-restart
6. Worker pool configuration (worker count, ports, health intervals) is in `config/python/py4j.json` — handler definitions are NOT in this file

### Admin UI

The Python Workers page (`/admin/python-workers`) provides real-time monitoring of the worker pool, with status cards for each worker, restart controls, log viewer, and configuration reload.
