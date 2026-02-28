"""
Echo Python Handler
====================
Sample Python handler that echoes back the payload — demonstrates
the Python handler contract for DGFacade.

This handler mirrors the built-in Java ECHO handler but runs
entirely in a Python worker process via Py4J.

Since: 1.6.1
"""

import platform
import os
from datetime import datetime, timezone
from dg_handler import DGHandler


class EchoPythonHandler(DGHandler):
    """
    Echoes the request payload back, augmented with Python runtime
    metadata. Useful for testing the Py4J bridge end-to-end.
    """

    def get_request_type(self) -> str:
        return "PYTHON_ECHO"

    def get_description(self) -> str:
        return "Echo handler implemented in Python — returns the payload as-is with runtime metadata"

    def compute(self, request: dict) -> dict:
        payload = request.get("payload", {})

        return {
            "echo": payload,
            "source": "PYTHON_PY4J",
            "pythonVersion": platform.python_version(),
            "platform": platform.platform(),
            "workerId": os.environ.get("DG_WORKER_ID", "unknown"),
            "pid": os.getpid(),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "requestId": request.get("requestId", "unknown"),
            "correlationId": request.get("correlationId"),
            # Show that we have access to app properties
            "appName": self.get_property("dgfacade.app-name", "DGFacade"),
            "version": self.get_property("dgfacade.version", "unknown")
        }
