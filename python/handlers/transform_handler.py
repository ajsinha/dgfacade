"""
Transform Python Handler
=========================
Sample Python handler that performs data transformations.
Demonstrates accessing payload data, performing computation,
and returning structured results.

Since: 1.6.1
"""

import hashlib
import json
import re
from collections import Counter
from dg_handler import DGHandler


class TransformPythonHandler(DGHandler):
    """
    Performs various text/data transformations based on the
    'operation' field in the payload.

    Supported operations:
        - UPPERCASE: Convert text to uppercase
        - LOWERCASE: Convert text to lowercase
        - REVERSE: Reverse the text
        - WORD_COUNT: Count words in the text
        - SHA256: Compute SHA-256 hash
        - WORD_FREQ: Word frequency analysis
        - JSON_FLATTEN: Flatten a nested JSON object
    """

    def get_request_type(self) -> str:
        return "PYTHON_TRANSFORM"

    def get_description(self) -> str:
        return "Data transformation handler implemented in Python"

    def start(self, request: dict) -> None:
        super().start(request)
        payload = request.get("payload", {})
        if "operation" not in payload:
            raise ValueError(
                "Missing 'operation' in payload. Supported: "
                "UPPERCASE, LOWERCASE, REVERSE, WORD_COUNT, SHA256, "
                "WORD_FREQ, JSON_FLATTEN"
            )

    def compute(self, request: dict) -> dict:
        payload = request.get("payload", {})
        operation = payload.get("operation", "").upper()
        text = payload.get("text", "")
        data = payload.get("data", {})

        operations = {
            "UPPERCASE":    lambda: {"result": text.upper(), "original": text},
            "LOWERCASE":    lambda: {"result": text.lower(), "original": text},
            "REVERSE":      lambda: {"result": text[::-1], "original": text},
            "WORD_COUNT":   lambda: self._word_count(text),
            "SHA256":       lambda: self._sha256(text),
            "WORD_FREQ":    lambda: self._word_freq(text),
            "JSON_FLATTEN": lambda: self._json_flatten(data),
        }

        if operation not in operations:
            raise ValueError(
                f"Unknown operation: '{operation}'. "
                f"Supported: {', '.join(sorted(operations.keys()))}"
            )

        result = operations[operation]()
        result["operation"] = operation
        return result

    # ── Operation implementations ─────────────────────────────────

    @staticmethod
    def _word_count(text: str) -> dict:
        words = text.split()
        return {
            "wordCount": len(words),
            "charCount": len(text),
            "lineCount": text.count("\n") + (1 if text else 0),
        }

    @staticmethod
    def _sha256(text: str) -> dict:
        digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
        return {"hash": digest, "algorithm": "SHA-256", "inputLength": len(text)}

    @staticmethod
    def _word_freq(text: str) -> dict:
        words = re.findall(r"\b\w+\b", text.lower())
        freq = Counter(words)
        top_10 = freq.most_common(10)
        return {
            "totalWords": len(words),
            "uniqueWords": len(freq),
            "topWords": [{"word": w, "count": c} for w, c in top_10],
        }

    @staticmethod
    def _json_flatten(data: dict, prefix: str = "") -> dict:
        """Flatten a nested dict into dot-notation keys."""
        items = {}
        for k, v in data.items():
            new_key = f"{prefix}.{k}" if prefix else k
            if isinstance(v, dict):
                items.update(TransformPythonHandler._json_flatten(v, new_key))
            elif isinstance(v, list):
                for i, item in enumerate(v):
                    if isinstance(item, dict):
                        items.update(
                            TransformPythonHandler._json_flatten(item, f"{new_key}[{i}]")
                        )
                    else:
                        items[f"{new_key}[{i}]"] = item
            else:
                items[new_key] = v
        return {"flattened": items, "keyCount": len(items)}
