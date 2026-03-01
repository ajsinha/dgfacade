# DGFacade — Data Gateway Facade

**Version:** 1.6.2
**Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved.**

---

## Overview

DGFacade is a high-performance, configuration-driven data gateway facade system designed to handle millions of concurrent requests across multiple communication channels through a unified handler model. Built on Apache Pekko actors and Spring Boot, it provides pluggable handler dispatch, multi-channel ingestion, distributed clustering, and enterprise-grade messaging across 6 broker types.

## Key Features

- **Multi-Channel Ingestion** — REST API, WebSocket, Kafka, ActiveMQ, and FileSystem ingesters with dedicated listeners
- **Actor-Based Execution** — Apache Pekko manages handler lifecycle with TTL enforcement and massive concurrency
- **Dynamic Configuration** — JSON-based handler configs with per-user overrides and hot reload
- **Handler Chaining** — Declarative pipeline composition with linear, conditional, and parallel execution phases
- **Distributed Clustering** — HTTP-based peer discovery, heartbeat tracking, and request forwarding across nodes
- **6 Broker Types** — Kafka, ActiveMQ, RabbitMQ, IBM MQ, FileSystem, SQL with SSL/TLS and PEM certificates
- **External Handler Loading** — Drop JAR files in `libs/` to add custom handlers at runtime
- **Dynamic Proxy** — DGHandlerProxy wraps any POJO into the DGHandler lifecycle automatically
- **Python Handlers** — Write handlers in Python using the same contract; registered in standard handler configs with `is_python: true`; Py4J worker pool with health monitoring, auto-restart, and Admin UI
- **Streaming Handlers** — Long-running handlers send incremental updates over WebSocket
- **Prometheus Metrics** — 13+ custom Micrometer metrics with pre-built Grafana dashboard
- **Health Check & Monitoring** — /ping, /health, live log viewer, all public (no auth required)
- **Metrics Time Series** — Real-time sparkline charts on the dashboard showing request rate, errors, heap, and latency
- **Dark Mode** — Full dark theme toggle with Ctrl+D keyboard shortcut
- **Config Validation** — Schema validation for brokers, channels, and ingesters via REST API
- **Bulk Config Export** — Download all configs as ZIP for environment migration
- **Keyboard Shortcuts** — Ctrl+K command palette, Ctrl+Shift+L logs, Ctrl+Shift+H health
- **Web Dashboard** — Real-time monitoring, admin UI for users/keys/channels, table pagination/sorting/search

## Technology Stack

| Component     | Technology              |
|---------------|-------------------------|
| Language      | Java 17, Scala 2.13, Python 3 |
| Framework     | Spring Boot 3.2.5       |
| Actors        | Apache Pekko 1.0.2      |
| Messaging     | Kafka 3.7, ActiveMQ 6.1 |
| Serialization | Jackson 2.17            |
| UI            | Thymeleaf, Bootstrap 5  |
| Monitoring    | Prometheus, Grafana     |
| Build         | Maven 3.8+              |

## Project Structure

```
dgfacade/
├── common/              Shared models (DGRequest, DGResponse, HandlerState), exceptions, utilities
├── messaging/           Pub/sub: Kafka, ActiveMQ, RabbitMQ, IBM MQ, FileSystem, SQL
├── server/              Pekko actors, execution engine, handler lifecycle, ingestion, clustering
├── web/                 Spring Boot app, REST API, WebSocket, admin UI, monitoring
├── docs/                Architecture, quickstart, tutorials
├── config/
│   ├── handlers/        Handler type mappings (JSON)
│   ├── brokers/         Broker connection configs (7 configs)
│   ├── input-channels/  Input channel configs (5 configs)
│   ├── output-channels/ Output channel configs (2 configs)
│   ├── ingesters/       Ingester configs (3 configs)
│   ├── chains/          Handler chain definitions
│   ├── python/          Python worker pool config (py4j.json)
│   ├── prometheus/      Prometheus scrape config
│   ├── grafana/         Pre-built Grafana dashboard JSON
│   ├── users.json       User accounts
│   └── apikeys.json     API key definitions
├── python/              Python handler code
│   ├── dgfacade_worker.py   Worker process entry point
│   ├── dg_handler.py        Base handler class
│   ├── dg_gateway.py        Py4J gateway bridge
│   └── handlers/            Python handler implementations
├── libs/                External handler JARs (drop-in)
├── logs/                Application and handler execution logs
├── build.sh             Build script
├── run.sh               Run script
└── pom.xml              Parent Maven POM
```

## Quick Start

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run -pl web

# Or use scripts
chmod +x build.sh run.sh
./build.sh
./run.sh
```

### Test

```bash
# Simple ping (text response — no auth)
curl http://localhost:8090/ping

# JSON ping with metadata (no auth)
curl http://localhost:8090/api/ping

# Full health check (no auth)
curl http://localhost:8090/api/health

# Echo request
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{"api_key":"dgf-test-key-0001","request_type":"ECHO","payload":{"message":"Hello!"}}'

# Arithmetic
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{"api_key":"dgf-test-key-0001","request_type":"ARITHMETIC","payload":{"operation":"MUL","operands":[7,6]}}'

# Python handler (requires enabled worker pool in config/python/py4j.json)
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{"api_key":"dgf-test-key-0001","request_type":"PYTHON_ECHO","payload":{"message":"Hello from Python!"}}'
```

### Web Dashboard

Open `http://localhost:8090` — Login: `admin` / `password`

## API Endpoints

| Method | Path                       | Auth     | Description                      |
|--------|----------------------------|----------|----------------------------------|
| GET    | /ping                      | Public   | Simple text ping ("pong")        |
| GET    | /api/ping                  | Public   | JSON ping with version/uptime    |
| GET    | /api/health                | Public   | Full JSON health check           |
| GET    | /health                    | Public   | Health check dashboard (UI)      |
| POST   | /api/v1/request            | API Key  | Submit a DGRequest               |
| GET    | /api/v1/handlers           | Public   | List registered handler types    |
| GET    | /api/v1/status             | Public   | Recent execution states          |
| POST   | /api/v1/reload             | Public   | Reload handler configs           |
| GET    | /api/v1/health             | Public   | Legacy health check              |
| GET    | /api/v1/ingesters          | Public   | List ingesters and stats         |
| GET    | /api/v1/logs/tail          | Public   | Tail log file (last N lines)     |
| GET    | /api/v1/logs/stream        | Public   | SSE stream of new log lines      |
| GET    | /api/v1/cluster/nodes      | Public   | Cluster node list                |
| GET    | /api/v1/cluster/status     | Public   | Cluster status summary           |
| GET    | /api/v1/metrics/snapshot   | Public   | Current metrics for sparklines   |
| GET    | /admin/api/config/export   | Admin    | Download all configs as ZIP      |
| POST   | /admin/api/validate/broker | Admin    | Validate broker config JSON      |
| WS     | /ws/gateway                | Public   | WebSocket gateway                |
| GET    | /api/v1/python/status      | Public   | Python worker pool status        |
| GET    | /api/v1/python/workers     | Public   | List Python worker statuses      |
| POST   | /api/v1/python/restart-all | Admin    | Restart all Python workers       |
| POST   | /api/v1/python/reload-config | Admin  | Reload Python config from disk   |

## Monitoring (No Auth Required)

All monitoring endpoints are accessible without authentication:

- **`/ping`** — Load balancer health check (returns "pong")
- **`/api/ping`** — JSON metadata (version, uptime)
- **`/health`** — System health dashboard with JVM metrics, component status
- **`/api/health`** — JSON health for automated monitoring
- **`/monitoring/logs`** — Live log viewer with SSE streaming, severity filtering, search
- **`/monitoring/handlers`** — Handler execution states
- **`/monitoring/cluster`** — Cluster node status
- **`/monitoring/ingestion`** — Ingestion statistics

## Keyboard Shortcuts

| Shortcut              | Action                    |
|-----------------------|---------------------------|
| `Ctrl+K`              | Command Palette           |
| `Ctrl+D`              | Toggle Dark Mode          |
| `Ctrl+Shift+L`        | Jump to Live Logs         |
| `Ctrl+Shift+H`        | Jump to Health Check      |
| `?`                   | Show shortcuts dialog     |

## Documentation

- [Architecture](docs/architecture.md)
- [Quick Start](docs/quickstart.md)
- [Tutorial: Custom Handlers](docs/tutorial.md)

## License

Copyright © 2025-2030, All Rights Reserved.
Ashutosh Sinha | Email: ajsinha@gmail.com
Proprietary and confidential.
