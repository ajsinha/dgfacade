# DGFacade Quick Start Guide

Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved.

## Prerequisites

- Java 17+
- Maven 3.8+
- (Optional) Python 3.8+ and `pip install py4j` for Python handlers
- (Optional) Apache Kafka 3.x for Kafka channel
- (Optional) Apache ActiveMQ 6.x for ActiveMQ channel

## Build

```bash
mvn clean package -DskipTests
```

## Configure

1. Review and edit `config/handlers/default.json` for handler mappings
2. Review `config/users.json` and `config/apikeys.json` for authentication
3. Edit `web/src/main/resources/application.properties` for server settings

## Run

```bash
# Using Spring Boot Maven plugin
mvn spring-boot:run -pl web

# Or using the JAR directly
java -jar web/target/dgfacade-web-1.6.1.jar
```

## Verify

```bash
# Simple ping (no auth required)
curl http://localhost:8090/ping

# JSON ping with metadata
curl http://localhost:8090/api/ping

# Full health check
curl http://localhost:8090/api/health

# Submit an ECHO request
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{
    "api_key": "dgf-test-key-0001",
    "request_type": "ECHO",
    "payload": {"message": "Hello DGFacade!"}
  }'

# Submit an ARITHMETIC request
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{
    "api_key": "dgf-test-key-0001",
    "request_type": "ARITHMETIC",
    "payload": {"operation": "ADD", "operands": [10, 20, 30]}
  }'
```

## Web UI

Open `http://localhost:8090` in your browser.

Default credentials:
- **Admin**: username=`admin`, password=`password`
- **User**: username=`testuser`, password=`password`

## Adding Custom Handlers

### Java Handlers

1. Implement `com.dgfacade.server.handler.DGHandler`
2. Package as a JAR file
3. Drop the JAR in the `./libs` directory
4. Add the handler mapping to `config/handlers/default.json`
5. Call `POST /api/v1/reload` or restart the application

### Python Handlers

1. Create a Python class extending `DGHandler` (from `python/dg_handler.py`)
2. Place it in `python/handlers/` directory
3. Register in `config/handlers/default.json` with `"is_python": true` and `"handler_class": "com.dgfacade.server.python.DGHandlerPython"`
4. Enable the worker pool: set `"enabled": true` in `config/python/py4j.json`
5. Restart DGFacade

See the [Python Integration Guide](/help/python) for full details.
