"""
DGFacade Python Handler Base Class
===================================
This module defines the base contract for all Python handlers in DGFacade.
The contract mirrors the Java DGHandler interface exactly:

    start(request)  → validate input, acquire resources
    execute()        → perform computation, return result
    stop(response)   → cleanup resources

The only difference from Java is that DGRequest and DGResponse are
plain Python dictionaries (JSON-serializable).

Since: 1.6.1
"""

import abc
import time
import traceback
from enum import Enum
from typing import Any, Dict, Optional


class HandlerStatus(Enum):
    """Mirrors com.dgfacade.common.handler.HandlerStatus"""
    CREATED = "CREATED"
    STARTING = "STARTING"
    READY = "READY"
    EXECUTING = "EXECUTING"
    STOPPING = "STOPPING"
    STOPPED = "STOPPED"
    FAILED = "FAILED"


class DGHandler(abc.ABC):
    """
    Abstract base class for all Python DGFacade handlers.

    Every Python handler MUST subclass this and implement:
        - get_request_type()  → str
        - get_description()   → str
        - start(request)      → None (or raise on error)
        - execute()           → dict (the result payload)
        - stop(response)      → None

    The lifecycle is identical to the Java DGHandler:
        CREATED → STARTING → READY → EXECUTING → STOPPING → STOPPED
                                        ↘ (on error) → FAILED

    Each instance handles exactly one request (prototype scope —
    the gateway creates a new instance per invocation).

    DGRequest dict structure:
    {
        "requestId": "uuid",
        "requestType": "PYTHON_ECHO",
        "apiKey": "dgf-dev-key-001",
        "correlationId": "optional-correlation-id",
        "payload": { ... },
        "timestamp": "2025-01-01T00:00:00Z",
        "metadata": { ... },
        "sourceChannel": "REST_API"
    }

    DGResponse dict structure (return from execute):
    {
        "status": "SUCCESS" | "ERROR",
        "requestId": "uuid",
        "handlerType": "PYTHON_ECHO",
        "result": { ... },           # for SUCCESS
        "errorCode": "...",          # for ERROR
        "errorMessage": "..."        # for ERROR
    }
    """

    def __init__(self):
        self._status: HandlerStatus = HandlerStatus.CREATED
        self._request: Optional[Dict[str, Any]] = None
        self._app_properties: Optional[Dict[str, str]] = None

    # ── Properties set by the gateway before lifecycle ────────────

    @property
    def request(self) -> Optional[Dict[str, Any]]:
        """The DGRequest dictionary (set by gateway before start())."""
        return self._request

    @property
    def payload(self) -> Dict[str, Any]:
        """Convenience: the payload sub-dict of the request."""
        if self._request and "payload" in self._request:
            return self._request["payload"]
        return {}

    @property
    def app_properties(self) -> Dict[str, str]:
        """Application properties from the Java environment."""
        return self._app_properties or {}

    @property
    def status(self) -> HandlerStatus:
        return self._status

    # ── Abstract methods — MUST implement ─────────────────────────

    @abc.abstractmethod
    def get_request_type(self) -> str:
        """Return the request type string (e.g., 'PYTHON_ECHO')."""
        ...

    @abc.abstractmethod
    def get_description(self) -> str:
        """Return a human-readable description of this handler."""
        ...

    @abc.abstractmethod
    def compute(self, request: Dict[str, Any]) -> Dict[str, Any]:
        """
        Core computation method. Receives the full DGRequest dict and
        must return a result dict that will be wrapped in a SUCCESS response.

        If this method raises an exception, the gateway will return
        an ERROR response with the exception details.

        Args:
            request: The DGRequest as a Python dictionary.

        Returns:
            A dict containing the result payload.

        Raises:
            Exception: If computation fails — will be converted to ERROR response.
        """
        ...

    # ── Lifecycle methods (override if needed) ────────────────────

    def start(self, request: Dict[str, Any]) -> None:
        """
        Initialize handler with the request. Override to validate
        input or acquire resources. Raise an exception to fail fast.

        Args:
            request: The DGRequest dictionary.
        """
        self._status = HandlerStatus.STARTING
        self._request = request
        self._status = HandlerStatus.READY

    def execute(self) -> Dict[str, Any]:
        """
        Execute the handler. The default implementation calls compute().
        Override only if you need custom lifecycle around compute().

        Returns:
            Full DGResponse dictionary.
        """
        self._status = HandlerStatus.EXECUTING
        start_time = time.time()

        try:
            result = self.compute(self._request)
            elapsed_ms = (time.time() - start_time) * 1000

            response = {
                "status": "SUCCESS",
                "requestId": self._request.get("requestId", "unknown"),
                "handlerType": self.get_request_type(),
                "result": result,
                "executionTimeMs": round(elapsed_ms, 2)
            }
            self._status = HandlerStatus.STOPPED
            return response

        except Exception as e:
            elapsed_ms = (time.time() - start_time) * 1000
            self._status = HandlerStatus.FAILED

            response = {
                "status": "ERROR",
                "requestId": self._request.get("requestId", "unknown"),
                "handlerType": self.get_request_type(),
                "errorCode": type(e).__name__,
                "errorMessage": str(e),
                "stackTrace": traceback.format_exc(),
                "executionTimeMs": round(elapsed_ms, 2)
            }
            return response

    def stop(self, response: Optional[Dict[str, Any]]) -> None:
        """
        Cleanup after execution. Always called, even on error
        (response will be the error response dict).

        Override to release resources, close connections, etc.
        """
        self._status = HandlerStatus.STOPPED

    # ── Utility methods ───────────────────────────────────────────

    def success(self, result: Dict[str, Any]) -> Dict[str, Any]:
        """Convenience: build a SUCCESS response dict."""
        return {
            "status": "SUCCESS",
            "requestId": self._request.get("requestId", "unknown") if self._request else "unknown",
            "handlerType": self.get_request_type(),
            "result": result
        }

    def error(self, error_code: str, error_message: str) -> Dict[str, Any]:
        """Convenience: build an ERROR response dict."""
        return {
            "status": "ERROR",
            "requestId": self._request.get("requestId", "unknown") if self._request else "unknown",
            "handlerType": self.get_request_type(),
            "errorCode": error_code,
            "errorMessage": error_message
        }

    def get_property(self, key: str, default: str = None) -> Optional[str]:
        """Look up an application property by key."""
        return self.app_properties.get(key, default)
