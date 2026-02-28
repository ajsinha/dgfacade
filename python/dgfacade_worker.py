#!/usr/bin/env python3
"""
DGFacade Python Worker Process
Copyright (c) 2025-2030 Ashutosh Sinha. All Rights Reserved.

This worker runs as a subprocess managed by PythonWorkerManager.
It listens on a TCP port and handles JSON-based requests from the Java side.

Protocol: Length-prefixed JSON over TCP
  → 4-byte big-endian length + UTF-8 JSON request
  ← 4-byte big-endian length + UTF-8 JSON response

Commands:
  {"command": "ping"}                          → {"status": "pong"}
  {"command": "execute", "handler_module": ...,
   "handler_class": ..., "request_json": ...,
   "config_json": ...}                         → handler result as JSON
  {"command": "shutdown"}                      → graceful shutdown
"""

import argparse
import importlib
import json
import os
import socket
import struct
import sys
import threading
import time
import traceback
from datetime import datetime, timezone

# Global application properties (passed from Java via environment variable)
APP_PROPERTIES = {}


def load_app_properties():
    """Load application properties from DGFACADE_PROPERTIES environment variable."""
    global APP_PROPERTIES
    props_json = os.environ.get("DGFACADE_PROPERTIES", "{}")
    try:
        APP_PROPERTIES = json.loads(props_json)
    except json.JSONDecodeError:
        APP_PROPERTIES = {}
    return APP_PROPERTIES


class DGHandlerBase:
    """
    Base class for Python DGFacade handlers.

    All Python handlers should extend this class and implement the `execute` method.
    The contract mirrors the Java DGHandler interface:

        def construct(self, config: dict) -> None
        def execute(self, request: dict, app_properties: dict) -> dict
        def stop(self) -> None
        def cleanup(self) -> None

    The `request` parameter is a dictionary representation of DGRequest:
        {
            "request_id": "...",
            "request_type": "...",
            "api_key": "...",
            "payload": { ... },
            "delivery_destination": "...",
            "ttl_minutes": 30,
            "resolved_user_id": "...",
            "source_channel": "..."
        }

    The return value must be a dictionary with at minimum:
        {
            "status": "SUCCESS" | "ERROR",
            "data": { ... },              # for SUCCESS
            "error_message": "..."         # for ERROR
        }
    """

    def construct(self, config):
        """Initialize with handler configuration dictionary."""
        self.config = config or {}

    def execute(self, request, app_properties):
        """
        Execute the handler logic.

        Args:
            request: Dictionary representation of DGRequest
            app_properties: Dictionary of all application properties from Spring

        Returns:
            Dictionary with 'status' and 'data' or 'error_message'
        """
        raise NotImplementedError("Handler must implement execute()")

    def stop(self):
        """Signal to stop processing."""
        pass

    def cleanup(self):
        """Final resource cleanup."""
        pass


# ─── Handler Cache ────────────────────────────────────────────────

_handler_cache = {}


def get_handler_instance(module_name, class_name):
    """Import and instantiate a handler class (cached)."""
    cache_key = f"{module_name}.{class_name}"
    if cache_key in _handler_cache:
        return _handler_cache[cache_key]

    try:
        mod = importlib.import_module(module_name)
        cls = getattr(mod, class_name)
        instance = cls()
        _handler_cache[cache_key] = instance
        return instance
    except Exception as e:
        raise ImportError(f"Cannot load handler {cache_key}: {e}")


# ─── Request Processing ──────────────────────────────────────────

def handle_execute(data):
    """Handle an 'execute' command from Java."""
    handler_module = data.get("handler_module", "")
    handler_class = data.get("handler_class", "")
    request_json = data.get("request_json", "{}")
    config_json = data.get("config_json", "{}")

    try:
        request = json.loads(request_json)
        config = json.loads(config_json)
    except json.JSONDecodeError as e:
        return {"status": "ERROR", "error_message": f"Invalid JSON input: {e}"}

    try:
        handler = get_handler_instance(handler_module, handler_class)
        handler.construct(config)
        result = handler.execute(request, APP_PROPERTIES)

        # Ensure result is a proper dict
        if not isinstance(result, dict):
            result = {"status": "SUCCESS", "data": {"result": str(result)}}
        if "status" not in result:
            result["status"] = "SUCCESS"
        if "data" not in result and result["status"] == "SUCCESS":
            result["data"] = {}

        return result

    except Exception as e:
        tb = traceback.format_exc()
        return {
            "status": "ERROR",
            "error_message": f"Python handler {handler_module}.{handler_class} failed: {e}",
            "traceback": tb
        }
    finally:
        try:
            handler.cleanup()
        except Exception:
            pass


def handle_request(data):
    """Route a command to the appropriate handler."""
    command = data.get("command", "")

    if command == "ping":
        return {
            "status": "pong",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "worker_id": WORKER_ID
        }
    elif command == "execute":
        return handle_execute(data)
    elif command == "shutdown":
        return {"status": "shutdown_ack"}
    else:
        return {"status": "ERROR", "error_message": f"Unknown command: {command}"}


# ─── TCP Server ───────────────────────────────────────────────────

def read_message(conn):
    """Read a length-prefixed JSON message."""
    raw_len = conn.recv(4)
    if len(raw_len) < 4:
        return None
    msg_len = struct.unpack(">I", raw_len)[0]
    if msg_len <= 0 or msg_len > 50 * 1024 * 1024:
        return None
    chunks = []
    remaining = msg_len
    while remaining > 0:
        chunk = conn.recv(min(remaining, 65536))
        if not chunk:
            return None
        chunks.append(chunk)
        remaining -= len(chunk)
    return json.loads(b"".join(chunks).decode("utf-8"))


def write_message(conn, data):
    """Write a length-prefixed JSON message."""
    payload = json.dumps(data).encode("utf-8")
    conn.sendall(struct.pack(">I", len(payload)) + payload)


def handle_connection(conn, addr):
    """Handle a single TCP connection."""
    try:
        data = read_message(conn)
        if data is None:
            return True

        response = handle_request(data)
        write_message(conn, response)

        # Check for shutdown
        if data.get("command") == "shutdown":
            return False
        return True

    except Exception as e:
        try:
            write_message(conn, {"status": "ERROR", "error_message": str(e)})
        except Exception:
            pass
        return True
    finally:
        conn.close()


WORKER_ID = 0


def main():
    global WORKER_ID
    parser = argparse.ArgumentParser(description="DGFacade Python Worker")
    parser.add_argument("--port", type=int, required=True, help="TCP port to listen on")
    parser.add_argument("--worker-id", type=int, default=0, help="Worker ID")
    args = parser.parse_args()

    WORKER_ID = args.worker_id

    # Load application properties
    load_app_properties()
    prop_count = len(APP_PROPERTIES)

    print(f"[Worker {WORKER_ID}] Starting on port {args.port} "
          f"(Python {sys.version.split()[0]}, {prop_count} app properties loaded)",
          flush=True)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.settimeout(1.0)  # 1-second accept timeout for graceful shutdown
    server.bind(("127.0.0.1", args.port))
    server.listen(10)

    print(f"[Worker {WORKER_ID}] READY — listening on 127.0.0.1:{args.port}", flush=True)

    running = True
    while running:
        try:
            conn, addr = server.accept()
            running = handle_connection(conn, addr)
        except socket.timeout:
            continue
        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"[Worker {WORKER_ID}] Server error: {e}", file=sys.stderr, flush=True)
            time.sleep(0.1)

    server.close()
    print(f"[Worker {WORKER_ID}] Shut down.", flush=True)


if __name__ == "__main__":
    main()
