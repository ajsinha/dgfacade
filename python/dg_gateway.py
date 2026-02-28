"""
DGFacade Py4J Gateway
======================
This is the Python-side entry point for the Py4J bridge. It runs as a
standalone Python process, connects to the Java GatewayServer, and
registers itself as a callback entry point.

When Java calls executeHandler(className, requestJson, appPropertiesJson),
this module:
  1. Dynamically imports and instantiates the requested Python handler class
  2. Passes the DGRequest dict and app properties to it
  3. Runs the handler lifecycle: start() → execute() → stop()
  4. Returns the DGResponse as a JSON string

Usage (called automatically by PythonWorkerProcess):
    python -m dg_gateway

Environment variables (set by PythonWorkerProcess):
    DG_WORKER_ID       - Worker identifier
    DG_GATEWAY_PORT    - Java GatewayServer port
    DG_CALLBACK_PORT   - Python callback server port
    DG_GATEWAY_HOST    - Java GatewayServer host (default: 127.0.0.1)

Since: 1.6.1
"""

import importlib
import json
import logging
import os
import sys
import traceback
import time

from py4j.java_gateway import JavaGateway, CallbackServerParameters, GatewayParameters
from py4j.clientserver import ClientServer, JavaParameters, PythonParameters

# ── Setup logging ─────────────────────────────────────────────────

logging.basicConfig(
    level=logging.INFO,
    format=f"[PY4J-Worker-{os.environ.get('DG_WORKER_ID', '?')}] "
           f"%(asctime)s %(levelname)s %(name)s: %(message)s",
    stream=sys.stdout
)
log = logging.getLogger("dg_gateway")


class DGPythonDelegate:
    """
    The Python delegate object registered with Py4J. Java calls methods
    on this object through the Py4J bridge.

    This is the SINGLE common entry point for all Python handler invocations.
    It receives the fully qualified Python class name, instantiates it,
    and runs the standard DGHandler lifecycle.
    """

    def __init__(self):
        self._handler_cache = {}  # class cache to avoid repeated imports
        self._requests_handled = 0
        self._errors = 0
        log.info("DGPythonDelegate initialized")

    def executeHandler(self, python_class_name: str,
                       dg_request_json: str,
                       app_properties_json: str) -> str:
        """
        Execute a Python handler class with the given DGRequest.

        This is the method called from Java via Py4J. It:
          1. Parses the JSON inputs
          2. Dynamically loads and instantiates the handler class
          3. Runs the lifecycle: start() → execute() → stop()
          4. Returns the DGResponse as a JSON string

        Args:
            python_class_name:   e.g., "handlers.echo_handler.EchoPythonHandler"
            dg_request_json:     DGRequest as JSON string
            app_properties_json: Application properties as JSON string

        Returns:
            DGResponse as JSON string
        """
        start_time = time.time()
        handler_instance = None

        try:
            # 1. Parse inputs
            request_dict = json.loads(dg_request_json)
            app_props = json.loads(app_properties_json) if app_properties_json else {}

            request_type = request_dict.get("requestType", "UNKNOWN")
            request_id = request_dict.get("requestId", "unknown")
            log.info("Executing handler %s for request %s (type=%s)",
                     python_class_name, request_id, request_type)

            # 2. Load and instantiate the handler class
            handler_class = self._load_class(python_class_name)
            handler_instance = handler_class()

            # 3. Inject app properties
            handler_instance._app_properties = app_props

            # 4. Lifecycle: start
            handler_instance.start(request_dict)

            # 5. Lifecycle: execute (returns DGResponse dict)
            response_dict = handler_instance.execute()

            # 6. Lifecycle: stop
            handler_instance.stop(response_dict)

            self._requests_handled += 1
            elapsed = (time.time() - start_time) * 1000
            log.info("Handler %s completed in %.1fms (request=%s, status=%s)",
                     python_class_name, elapsed, request_id,
                     response_dict.get("status", "?"))

            return json.dumps(response_dict)

        except Exception as e:
            self._errors += 1
            elapsed = (time.time() - start_time) * 1000
            log.error("Handler %s failed after %.1fms: %s",
                      python_class_name, elapsed, str(e))
            log.error(traceback.format_exc())

            # Build error response
            error_response = {
                "status": "ERROR",
                "requestId": "unknown",
                "handlerType": python_class_name,
                "errorCode": type(e).__name__,
                "errorMessage": str(e),
                "stackTrace": traceback.format_exc()
            }

            # Try to get requestId from the parsed request
            try:
                if 'request_dict' in dir():
                    error_response["requestId"] = request_dict.get("requestId", "unknown")
            except Exception:
                pass

            # Lifecycle: stop (always called, even on error)
            if handler_instance is not None:
                try:
                    handler_instance.stop(error_response)
                except Exception as stop_err:
                    log.warning("Error in handler stop(): %s", str(stop_err))

            return json.dumps(error_response)

    def ping(self) -> str:
        """Health check — Java calls this to verify the worker is alive."""
        return "pong"

    def getStats(self) -> str:
        """Return basic stats as JSON."""
        return json.dumps({
            "requestsHandled": self._requests_handled,
            "errors": self._errors,
            "workerId": os.environ.get("DG_WORKER_ID", "?"),
            "pid": os.getpid()
        })

    def _load_class(self, fully_qualified_name: str):
        """
        Dynamically import and return the handler class.

        Args:
            fully_qualified_name: e.g., "handlers.echo_handler.EchoPythonHandler"

        Returns:
            The handler class (not an instance).
        """
        if fully_qualified_name in self._handler_cache:
            return self._handler_cache[fully_qualified_name]

        # Split "handlers.echo_handler.EchoPythonHandler" into
        # module_path="handlers.echo_handler", class_name="EchoPythonHandler"
        parts = fully_qualified_name.rsplit(".", 1)
        if len(parts) != 2:
            raise ImportError(
                f"Invalid Python class name: '{fully_qualified_name}'. "
                f"Expected format: 'module.path.ClassName'"
            )

        module_path, class_name = parts

        try:
            module = importlib.import_module(module_path)
            cls = getattr(module, class_name)
        except ModuleNotFoundError as e:
            raise ImportError(
                f"Cannot find module '{module_path}': {e}. "
                f"Check PYTHONPATH includes the handler directory."
            ) from e
        except AttributeError as e:
            raise ImportError(
                f"Module '{module_path}' has no class '{class_name}': {e}"
            ) from e

        self._handler_cache[fully_qualified_name] = cls
        log.info("Loaded handler class: %s", fully_qualified_name)
        return cls

    class Java:
        """Py4J interface declaration — tells Py4J which methods to expose."""
        implements = [
            "com.dgfacade.server.python.PythonWorkerProcess$PythonDelegateInterface"
        ]


def main():
    """
    Entry point. Starts the Py4J callback server and registers
    the DGPythonDelegate as the Python entry point.
    """
    worker_id = os.environ.get("DG_WORKER_ID", "0")
    gateway_port = int(os.environ.get("DG_GATEWAY_PORT", "25333"))
    callback_port = int(os.environ.get("DG_CALLBACK_PORT", "25433"))
    gateway_host = os.environ.get("DG_GATEWAY_HOST", "127.0.0.1")

    log.info("=" * 60)
    log.info("  DGFacade Python Worker %s Starting", worker_id)
    log.info("  PID: %d", os.getpid())
    log.info("  Gateway: %s:%d, Callback port: %d",
             gateway_host, gateway_port, callback_port)
    log.info("  Python: %s", sys.version)
    log.info("  PYTHONPATH: %s", os.environ.get("PYTHONPATH", "(not set)"))
    log.info("=" * 60)

    delegate = DGPythonDelegate()

    # Connect to the Java GatewayServer and register the callback
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(
            address=gateway_host,
            port=gateway_port,
            auto_convert=True
        ),
        callback_server_parameters=CallbackServerParameters(
            port=callback_port,
            daemonize=True,
            daemonize_connections=True
        ),
        python_server_entry_point=delegate
    )

    log.info("Connected to Java GatewayServer on %s:%d", gateway_host, gateway_port)
    log.info("Python callback server started on port %d", callback_port)
    log.info("Worker %s is READY — waiting for handler invocations...", worker_id)

    # Keep the process alive
    try:
        while True:
            time.sleep(60)
            log.debug("Worker %s alive (handled=%d, errors=%d)",
                      worker_id, delegate._requests_handled, delegate._errors)
    except KeyboardInterrupt:
        log.info("Worker %s shutting down (KeyboardInterrupt)", worker_id)
    finally:
        gateway.shutdown()
        log.info("Worker %s shutdown complete", worker_id)


if __name__ == "__main__":
    main()
