# DGFacade v1.0.0

## Configuration-Driven Facade Service

Copyright © 2025-2030, All Rights Reserved - Ashutosh Sinha | Email: ajsinha@gmail.com

**Patent Pending**: Certain architectural patterns and implementations may be subject to patent applications.

---

## Overview

DGFacade is a high-performance, configuration-driven service that accepts JSON requests via REST API, Apache Kafka, and ActiveMQ. It features an Apache Pekko actor-based handler execution framework for massive concurrency, a full-featured web UI with authentication, and admin functionality.

## Architecture

```
┌─────────────┐  ┌──────────┐  ┌───────────┐
│  REST API   │  │  Kafka   │  │ ActiveMQ  │
└──────┬──────┘  └─────┬────┘  └─────┬─────┘
       │               │              │
       └───────────────┼──────────────┘
                       │
              ┌────────▼─────────┐
              │  Request         │
              │  Dispatcher      │
              │  (API Key Auth)  │
              └────────┬─────────┘
                       │
              ┌────────▼─────────┐
              │  Apache Pekko    │
              │  Actor System    │
              └────────┬─────────┘
                       │
              ┌────────▼─────────┐
              │  Handler         │
              │  start()→execute()│
              │  →stop()         │
              └──────────────────┘
```

## Project Structure

| Module | Description |
|--------|-------------|
| `dgfacade-common` | Shared models, interfaces, configuration, utilities |
| `dgfacade-messaging` | Kafka and ActiveMQ integration |
| `dgfacade-server` | Pekko actor system, handler execution, services |
| `dgfacade-web` | Spring Boot application, REST API, Web UI |

## Prerequisites

- Java 17+
- Maven 3.8+
- (Optional) Apache Kafka for messaging
- (Optional) Apache ActiveMQ for messaging

## Build

```bash
cd dgfacade-parent
mvn clean install
```

## Run

```bash
cd dgfacade-web
mvn spring-boot:run
```

Access: http://localhost:8080

## Default Credentials

| User | Password | Roles |
|------|----------|-------|
| admin | admin123 | ADMIN, USER |
| operator | operator123 | USER |
| viewer | viewer123 | VIEWER |

## REST API

### Submit Request
```
POST /api/v1/request
Content-Type: application/json

{
  "apiKey": "dgf-dev-key-001",
  "requestType": "ARITHMETIC",
  "payload": {
    "operation": "ADD",
    "operandA": 10,
    "operandB": 5
  }
}
```

### Health Check
```
GET /api/v1/health
```

### List Handlers
```
GET /api/v1/handlers
```

## Configuration

Edit `application.properties` for:
- Server port, application name
- Kafka/ActiveMQ enable/disable and connection settings
- Actor system tuning (pool sizes, timeouts)
- Security file paths

### External Libraries
Place `.jar` files in the `libs/` directory - they are automatically loaded to the classpath at startup.

### Users & API Keys
Managed via JSON files in `config/` directory:
- `config/users.json` - User accounts
- `config/apikeys.json` - API keys

## Creating Custom Handlers

Implement `DGHandler` interface:

```java
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MyHandler implements DGHandler {
    public String getRequestType() { return "MY_TYPE"; }
    public String getDescription() { return "My custom handler"; }
    public void start(DGRequest request) { /* init */ }
    public DGResponse execute() { /* business logic */ }
    public void stop(DGResponse response) { /* cleanup */ }
    public HandlerStatus getStatus() { return status; }
}
```

Handlers are auto-discovered by Spring at startup.

## License

Proprietary and confidential. Unauthorized use prohibited.
