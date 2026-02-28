# DGFacade Tutorial: Building a Custom Handler

Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved.

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
curl -X POST http://localhost:8090/api/v1/request \
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

## Python Handler Tutorial

Python handlers follow the same contract as Java handlers. No JAR packaging needed — just write a Python class.

### Step 1: Create Handler

Create `python/handlers/sentiment_handler.py`:

```python
from dg_handler import DGHandler
from datetime import datetime, timezone

class SentimentHandler(DGHandler):
    """Simple sentiment analysis handler."""

    def start(self, config, app_context):
        self.positive_words = config.get("positive_words", ["good", "great", "excellent", "happy"])
        self.negative_words = config.get("negative_words", ["bad", "terrible", "awful", "sad"])

    def compute(self, request):
        text = request.get("payload", {}).get("text", "").lower()
        words = text.split()
        pos = sum(1 for w in words if w in self.positive_words)
        neg = sum(1 for w in words if w in self.negative_words)
        sentiment = "positive" if pos > neg else ("negative" if neg > pos else "neutral")

        return {
            "requestId": request.get("requestId"),
            "status": "SUCCESS",
            "payload": {
                "sentiment": sentiment,
                "positive_count": pos,
                "negative_count": neg,
                "word_count": len(words)
            },
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
```

### Step 2: Register in Configuration

Add to `config/handlers/default.json` — note the `is_python: true` flag:

```json
{
  "SENTIMENT": {
    "handler_class": "com.dgfacade.server.python.DGHandlerPython",
    "description": "Python sentiment analysis handler",
    "ttl_minutes": 5,
    "enabled": true,
    "is_python": true,
    "config": {
      "python_module": "handlers.sentiment_handler",
      "python_class": "SentimentHandler",
      "positive_words": ["good", "great", "excellent", "happy", "love"],
      "negative_words": ["bad", "terrible", "awful", "sad", "hate"]
    }
  }
}
```

### Step 3: Enable Worker Pool

Set `"enabled": true` in `config/python/py4j.json` and restart DGFacade.

### Step 4: Test

```bash
curl -X POST http://localhost:8090/api/v1/request \
  -H "Content-Type: application/json" \
  -d '{
    "api_key": "dgf-test-key-0001",
    "request_type": "SENTIMENT",
    "payload": {"text": "This is a great and excellent product!"}
  }'
```
