# DGFacade — Data Gateway Facade

**Version:** 1.4.0
**Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved.**
**Patent Pending:** Certain architectural patterns and implementations may be subject to patent applications.

---

## Overview

DGFacade is a high-performance, configuration-driven data gateway facade system designed to handle millions of concurrent requests across multiple communication channels through a unified handler model.

## Features

- **Multi-Channel Support** — REST API, WebSocket, Apache Kafka, ActiveMQ, FileSystem, SQL
- **Actor-Based Execution** — Apache Pekko manages handler lifecycle with TTL enforcement
- **Dynamic Configuration** — JSON-based handler configs with per-user overrides and hot reload
- **External Handler Loading** — Drop JAR files in `libs/` to add custom handlers at runtime
- **Streaming Handlers** — Long-running handlers send incremental updates
- **Backpressure** — Queue depth monitoring; messages stay on broker when limits exceeded
- **Auto-Recovery** — Automatic broker reconnection with configurable intervals
- **Web Dashboard** — Real-time monitoring, admin UI for users/keys/channels
- **Security** — BCrypt passwords, role-based access (ADMIN/USER), per-key rate limiting

## Technology Stack

| Component     | Technology              |
|---------------|-------------------------|
| Language      | Java 17, Scala 2.13     |
| Framework     | Spring Boot 3.2.5       |
| Actors        | Apache Pekko 1.0.2      |
| Messaging     | Kafka 3.7, ActiveMQ 6.1 |
| Serialization | Jackson 2.17            |
| UI            | Thymeleaf, Bootstrap 5  |
| Build         | Maven 3.8+              |

## Project Structure

```
dgfacade/
├── common/              Shared models, exceptions, utilities
├── messaging/           Pub/sub: Kafka, ActiveMQ, FileSystem, SQL
├── server/              Pekko actors, execution engine, handler lifecycle
├── web/                 Spring Boot app, REST API, WebSocket, admin UI
├── docs/                Architecture, quickstart, tutorials
├── config/
│   ├── handlers/        Handler type mappings (JSON)
│   ├── users.json       User accounts
│   └── apikeys.json     API key definitions
├── libs/                External handler JARs (drop-in)
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
# Health check
curl http://localhost:8090/api/v1/health

# Echo request
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{"api_key":"dgf-test-key-0001","request_type":"ECHO","payload":{"message":"Hello!"}}'

# Arithmetic
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{"api_key":"dgf-test-key-0001","request_type":"ARITHMETIC","payload":{"operation":"MUL","operands":[7,6]}}'
```

### Web Dashboard

Open `http://localhost:8090` — Login: `admin` / `password`

## API Endpoints

| Method | Path                | Description                    |
|--------|---------------------|--------------------------------|
| POST   | /api/v1/request     | Submit a DGRequest             |
| GET    | /api/v1/handlers    | List registered handler types  |
| GET    | /api/v1/status      | Recent execution states        |
| POST   | /api/v1/reload      | Reload handler configs         |
| GET    | /api/v1/health      | Health check                   |
| WS     | /ws/gateway         | WebSocket gateway              |

## Documentation

- [Architecture](docs/architecture.md)
- [Quick Start](docs/quickstart.md)
- [Tutorial: Custom Handlers](docs/tutorial.md)

## License

Copyright © 2025-2030, All Rights Reserved.
Ashutosh Sinha | Email: ajsinha@gmail.com
Proprietary and confidential. Patent Pending.
