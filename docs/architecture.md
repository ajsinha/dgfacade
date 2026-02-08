# DGFacade Architecture

Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved. Patent Pending.

## Overview

DGFacade is a configuration-driven data gateway facade system designed for high-performance,
multi-channel request handling. It processes millions of concurrent requests through a unified
handler model backed by Apache Pekko actors.

## Module Structure

```
dgfacade/
├── common/     - Shared models, exceptions, and utilities
├── messaging/  - Abstract pub/sub with Kafka, ActiveMQ, FileSystem, SQL implementations
├── server/     - Pekko actor execution engine, handler lifecycle management
├── web/        - Spring Boot application, REST API, WebSocket, admin UI
└── docs/       - Project documentation
```

## Request Flow

1. Request arrives via REST, WebSocket, Kafka, ActiveMQ, or FileSystem channel
2. Request is normalized to a `DGRequest` object
3. `ExecutionEngine` validates the API key and resolves the user
4. Handler configuration is looked up (user-specific override or default)
5. `HandlerSupervisor` spawns a `HandlerActor` child
6. `HandlerActor` instantiates the handler class and calls `construct(config)`
7. Actor schedules a TTL timeout message
8. Actor calls `execute(request)` or `executeStreaming(request, updateSink)`
9. On completion, timeout, or failure: `stop()` → `cleanup()` → actor terminates
10. `HandlerState` is logged to the `CircularBuffer` and handler-executions.log
11. Response is sent back via the originating channel or `delivery_destination`

## Actor Hierarchy

```
ActorSystem("dgfacade")
└── HandlerSupervisor
    ├── HandlerActor (handler-abc123)
    ├── HandlerActor (handler-def456)
    └── ... (millions of concurrent actors)
```

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
- **Web UI**: Session-based with form login, BCrypt password hashing
- **Roles**: ADMIN (full access), USER (dashboard and monitoring only)
- **API Keys**: Per-user, with allowed request types and rate limiting
