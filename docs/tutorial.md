# DGFacade Tutorial: Building a Custom Handler

Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved. Patent Pending.

## Step 1: Implement DGHandler

```java
package com.example.handlers;

import com.dgfacade.common.model.DGRequest;
import com.dgfacade.common.model.DGResponse;
import com.dgfacade.server.handler.DGHandler;
import java.util.Map;

public class WeatherHandler implements DGHandler {

    private Map<String, Object> config;

    @Override
    public void construct(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public DGResponse execute(DGRequest request) {
        String city = (String) request.getPayload().get("city");
        // Your weather API logic here
        Map<String, Object> weather = Map.of(
            "city", city,
            "temperature", "72°F",
            "condition", "Sunny"
        );
        return DGResponse.success(request.getRequestId(), weather);
    }

    @Override
    public void stop() { }

    @Override
    public void cleanup() { }
}
```

## Step 2: Package as JAR

```bash
mvn clean package
cp target/weather-handler-1.0.jar /path/to/dgfacade/libs/
```

## Step 3: Register in Configuration

Add to `config/handlers/default.json`:

```json
{
  "WEATHER": {
    "handler_class": "com.example.handlers.WeatherHandler",
    "description": "Fetches current weather for a city",
    "ttl_minutes": 5,
    "config": {
      "api_url": "https://api.weather.example.com"
    }
  }
}
```

## Step 4: Test

```bash
curl -X POST http://localhost:8080/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{
    "api_key": "dgf-test-key-0001",
    "request_type": "WEATHER",
    "payload": {"city": "New York"}
  }'
```

## Streaming Handler Example

For long-running operations that need to send incremental updates:

```java
public class DataSyncHandler implements StreamingDGHandler {

    @Override
    public void construct(Map<String, Object> config) { }

    @Override
    public DGResponse execute(DGRequest request) {
        return executeStreaming(request, update -> {});
    }

    @Override
    public DGResponse executeStreaming(DGRequest request, Consumer<DGResponse> updateSink) {
        List<String> tables = (List<String>) request.getPayload().get("tables");
        for (int i = 0; i < tables.size(); i++) {
            // Process each table
            updateSink.accept(DGResponse.success(request.getRequestId(),
                Map.of("progress", (i + 1) + "/" + tables.size(),
                       "table", tables.get(i),
                       "status", "synced")));
        }
        return DGResponse.success(request.getRequestId(),
            Map.of("total_synced", tables.size()));
    }

    @Override
    public void stop() { }

    @Override
    public void cleanup() { }
}
```
