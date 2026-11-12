#!/usr/bin/env python3
"""
FitCheckAI — Track C (C-203 & C-204) Standalone Virtual Try-On POC & Evaluation Harness.

Implements:
- CT-06 / CT-07 input validation (views, revisions, ownership, byte caps, anti-front-as-back).
- Hard Consent Gate, Budget/Attempt Cap, and Kill Switch before any live network call.
- Provider adapters: FakeVtoProviderAdapter (offline tests), FashnProviderAdapter, VertexVtoProviderAdapter.
- Explicit capability distinction:
  * Single view vs Two independent requests vs Native paired generation.
  * Single garment vs Sequential multi-garment pass (with pass-1 drift audit flag).
- Private output storage (0700 dir / 0600 files outside Git) + zero-PII sanitized ledger.
- Full attempt accounting (records every failure/timeout — never cherry-picks) & 2-reviewer rubric manifest.
"""

from __future__ import annotations

import argparse
import base64
import copy
import dataclasses
from dataclasses import dataclass, field
import hashlib
import json
import os
from pathlib import Path
import re
import time
from typing import Any, Dict, List, Optional, Protocol, Sequence, Set, Tuple
import urllib.error
import urllib.request

# CT-07 Wire & Decoded Payload Caps
MAX_PERSON_VIEW_BYTES = 2 * 1024 * 1024       # 2 MiB per person view
MAX_GARMENT_VIEW_BYTES = 1 * 1024 * 1024      # 1 MiB per garment view
MAX_TOTAL_DECODED_BYTES = 8 * 1024 * 1024     # 8 MiB total decoded per request
MAX_WIRE_PAYLOAD_BYTES = 12 * 1024 * 1024     # 12 MiB wire JSON ceiling

DEFAULT_SUPPORTED_CATEGORIES = frozenset({"TOP", "BOTTOM", "DRESS", "OUTERWEAR"})
VALID_VIEWS = frozenset({"FRONT", "BACK"})
ALLOWED_MIMES = frozenset({"image/jpeg", "image/png", "image/webp"})

# Minimal valid 1x1 PNG header + deterministic synthetic payload prefix
_PNG_1X1_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
)


class PocValidationError(Exception):
    """Typed CT-07 validation or gate error raised before billing/upload."""

    def __init__(self, code: str, message: str, retryable: bool = False) -> None:
        super().__init__(f"[{code}] {message}")
        self.code = code
        self.message = message
        self.retryable = retryable


@dataclass(frozen=True)
class BudgetPolicy:
    kill_switch_active: bool = False
    allow_live_calls: bool = False
    pre001_hackathon_cleared: bool = False
    max_attempts: int = 10
    approved_budget_usd: float = 0.0
    cost_per_credit_usd: float = 0.075


@dataclass
class AttemptRecord:
    attempt_index: int
    case_id: str
    view: str
    pass_index: int
    garment_item_id: str
    provider_id: str
    model_version: str
    source: str  # "FAKE" | "AI_LIVE"
    prediction_id: Optional[str]
    status: str  # "SUCCEEDED" | "FAILED" | "TIMED_OUT" | "BLOCKED"
    error_code: Optional[str]
    error_message: Optional[str]
    latency_ms: int
    credits_used: float
    estimated_cost_usd: float
    private_output_file: Optional[str]  # Relative filename inside private dir only, never full PII path


@dataclass
class CaseEvaluationRecord:
    case_id: str
    outfit_id: str
    requested_views: List[str]
    pair_execution_mode: str  # "SINGLE_VIEW_FRONT" | "SINGLE_VIEW_BACK" | "TWO_INDEPENDENT_REQUESTS" | "NATIVE_PAIRED"
    garment_application_mode: str  # "SINGLE_GARMENT" | "SEQUENTIAL_MULTI_GARMENT" | "NONE"
    applied_item_ids: List[str]
    unapplied_item_ids: List[str]
    requires_sequential_drift_check: bool
    overall_status: str  # "SUCCEEDED" | "PARTIAL" | "FAILED" | "REJECTED_PRE_UPLOAD"
    rejection_code: Optional[str]
    rejection_message: Optional[str]
    total_latency_ms: int
    total_cost_usd: float
    warnings: List[str] = field(default_factory=list)
    attempts: List[AttemptRecord] = field(default_factory=list)
    rubric_template: Dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class ProviderCallInput:
    case_id: str
    view: str
    pass_index: int
    garment_item_id: str
    garment_category: str
    person_image_bytes: bytes
    person_mime: str
    garment_image_bytes: bytes
    garment_mime: str
    timeout_seconds: float


@dataclass(frozen=True)
class ProviderCallOutput:
    prediction_id: str
    status: str  # "SUCCEEDED" | "FAILED" | "TIMED_OUT"
    output_bytes: Optional[bytes]
    credits_used: float
    error_code: Optional[str] = None
    error_message: Optional[str] = None


class VtoProviderAdapter(Protocol):
    @property
    def provider_id(self) -> str: ...

    @property
    def model_version(self) -> str: ...

    @property
    def is_live(self) -> bool: ...

    @property
    def supports_native_paired_views(self) -> bool: ...

    @property
    def supported_categories(self) -> FrozenSet[str]: ...

    @property
    def max_garments_per_call(self) -> int: ...

    def run_single_pass(self, call_input: ProviderCallInput) -> ProviderCallOutput: ...


def sanitize_diagnostic(text: str) -> str:
    """Redacts API keys, Bearer tokens, base64 data URIs, HTTP(S) URLs, and absolute filesystem paths from logs."""
    redacted = re.sub(r"sk-[A-Za-z0-9_-]{8,}", "[REDACTED_OPENAI_KEY]", text)
    redacted = re.sub(r"Bearer\s+[A-Za-z0-9._-]+", "Bearer [REDACTED]", redacted, flags=re.IGNORECASE)
    redacted = re.sub(r"data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=]+", "[REDACTED_BASE64]", redacted)
    redacted = re.sub(r"https?://[^\s\"'>]+", "[REDACTED_URL]", redacted)
    redacted = re.sub(r"(/[A-Za-z0-9._-]+){2,}", "[REDACTED_PATH]", redacted)
    return redacted


OPENAI_VTO_PROMPT_VERSION = "openai-vto-edit-v1.0"

VERIFIED_OPENAI_MODELS: Dict[str, Dict[str, Any]] = {
    "gpt-image-2.6-sunburst": {"text_in": 5.0, "img_in": 8.0, "img_out": 30.0, "fidelity": "none", "est_med": 0.10},
    "gpt-image-2.6-flare": {"text_in": 5.0, "img_in": 8.0, "img_out": 30.0, "fidelity": "none", "est_med": 0.085},
    "gpt-image-2.5-sunburst": {"text_in": 5.0, "img_in": 8.0, "img_out": 30.0, "fidelity": "none", "est_med": 0.10},
    "gpt-image-2.5-flare": {"text_in": 5.0, "img_in": 8.0, "img_out": 30.0, "fidelity": "none", "est_med": 0.085},
    "gpt-image-2": {"text_in": 2.5, "img_in": 4.0, "img_out": 15.0, "fidelity": "none", "est_med": 0.06},
    "gpt-image-1.5": {"text_in": 5.0, "img_in": 8.0, "img_out": 32.0, "fidelity": "low_or_high", "est_med": 0.14},
    "gpt-image-1-mini": {"text_in": 2.0, "img_in": 2.5, "img_out": 8.0, "fidelity": "low_only", "est_med": 0.03},
}

RETIRED_OPENAI_MODELS = frozenset({"dall-e-2", "dall-e-3", "gpt-image-1"})


def build_openai_vto_prompt(view: str, category: str) -> str:
    view_desc = (
        "rear-facing full-body view (back of person and back of garment)"
        if view == "BACK"
        else "front-facing full-body view (front of person and front of garment)"
    )
    role_map = {
        "TOP": ("upper-body top garment", "trousers/skirt, footwear, and unselected items"),
        "OUTERWEAR": ("outerwear jacket/coat", "trousers/skirt, footwear, inner base garment, and unselected items"),
        "BOTTOM": ("lower-body bottom garment", "upper-body top/shirt, footwear, and unselected items"),
        "DRESS": ("one-piece dress", "footwear and unselected accessories"),
    }
    garment_role, keep_region = role_map.get(category, ("garment", "unselected clothing items"))
    return "\n".join(
        [
            f"[PROMPT_VERSION: {OPENAI_VTO_PROMPT_VERSION}]",
            "Task: Photorealistic 2D Virtual Try-On image edit.",
            f"- Image 1 (first attached image): Base photograph of the real person ({view_desc}). Use Image 1 as the base image.",
            f"- Image 2 (second attached image): Reference photograph of the {garment_role} ({view_desc}). Use Image 2 as the clothing reference.",
            f"- Replace ONLY the relevant {garment_role} on the person in Image 1 with the garment from Image 2.",
            "- Preserve the person's identity, facial appearance, body proportions, pose, hairstyle, skin tone, background, and lighting.",
            "- Preserve garment color, collar shape, sleeve length, visible logo, visible printed text, and garment patterns.",
            f"- Do NOT create a different person, do NOT add unrelated accessories, and do NOT modify {keep_region}.",
            "- Make the result photorealistic.",
        ]
    )


def _encode_multipart_formdata(
    fields: Sequence[Tuple[str, str]],
    files: Sequence[Tuple[str, str, str, bytes]],
) -> Tuple[str, bytes]:
    boundary = "----FitCheckOpenAiVtoBoundary7MA4YWxkTrZu0gW"
    chunks: List[bytes] = []
    for name, value in fields:
        chunks.append(f"--{boundary}\r\n".encode("ascii"))
        chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        chunks.append(value.encode("utf-8"))
        chunks.append(b"\r\n")
    for field_name, filename, content_type, data in files:
        chunks.append(f"--{boundary}\r\n".encode("ascii"))
        chunks.append(
            f'Content-Disposition: form-data; name="{field_name}"; filename="{filename}"\r\n'.encode("utf-8")
        )
        chunks.append(f"Content-Type: {content_type}\r\n\r\n".encode("ascii"))
        chunks.append(data)
        chunks.append(b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode("ascii"))
    return f"multipart/form-data; boundary={boundary}", b"".join(chunks)


def _has_valid_raster_magic(data: bytes) -> bool:
    if len(data) < 24:
        return False
    if data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR" and b"IEND" in data:
        return True
    if data[:2] == b"\xff\xd8":
        return True
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return True
    return False


class OpenAiImageEditProviderAdapter:
    """
    Official OpenAI Images Edit API (`POST /v1/images/edits`) adapter for FitCheckAI Track C.
    Supports both offline schema-verified mock execution (`offline_mock=True`) and gated LIVE inference (`offline_mock=False`).
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: Optional[str] = None,
        quality: str = "medium",
        size: str = "1024x1536",
        output_format: str = "png",
        base_url: Optional[str] = None,
        allow_custom_model: bool = False,
        offline_mock: bool = False,
        mock_scenario: str = "success",
        transport_fn: Optional[Any] = None,
    ) -> None:
        resolved_model = (model_name or os.environ.get("OPENAI_IMAGE_MODEL") or "gpt-image-2").strip()
        if resolved_model.lower() in RETIRED_OPENAI_MODELS:
            raise PocValidationError(
                "UNSUPPORTED_CAPABILITY",
                f"OpenAI model '{resolved_model}' is retired or unsupported for multi-image VTO editing.",
            )
        if resolved_model not in VERIFIED_OPENAI_MODELS and not allow_custom_model:
            raise PocValidationError(
                "UNSUPPORTED_CAPABILITY",
                f"OpenAI model '{resolved_model}' is not in verified GPT Image catalog (set allow_custom_model=True to override).",
            )
        if quality not in ("low", "medium", "high", "auto"):
            raise PocValidationError("INVALID_REQUEST", f"Unsupported OpenAI quality '{quality}'.")
        if output_format not in ("png", "jpeg", "webp"):
            raise PocValidationError("INVALID_REQUEST", f"Unsupported OpenAI output_format '{output_format}'.")

        self._offline_mock = offline_mock
        self._mock_scenario = mock_scenario
        self._api_key = (
            "offline-mock-openai-token"
            if offline_mock
            else (api_key or os.environ.get("OPENAI_API_KEY", "")).strip()
        )
        self._model_name = resolved_model
        self._quality = quality
        self._size = size
        self._output_format = output_format
        self._base_url = (base_url or os.environ.get("OPENAI_API_BASE_URL") or "https://api.openai.com/v1").rstrip("/")
        self._transport_fn = transport_fn
        self.call_count = 0
        self.last_multipart_summary: Optional[Dict[str, Any]] = None

    @property
    def provider_id(self) -> str:
        return "openai"

    @property
    def model_version(self) -> str:
        suffix = "-offline-mock" if self._offline_mock else f"-{self._quality}"
        return f"{self._model_name}{suffix}"

    @property
    def is_live(self) -> bool:
        return not self._offline_mock

    @property
    def supports_native_paired_views(self) -> bool:
        return False

    @property
    def supported_categories(self) -> frozenset[str]:
        return DEFAULT_SUPPORTED_CATEGORIES

    @property
    def max_garments_per_call(self) -> int:
        return 1

    def _estimate_usd_from_usage(self, usage: Optional[Dict[str, Any]]) -> float:
        spec = VERIFIED_OPENAI_MODELS.get(self._model_name)
        if not spec:
            return 0.15
        if not usage:
            return float(spec["est_med"])
        details = usage.get("input_tokens_details") or {}
        text_tok = int(details.get("text_tokens") or 0)
        img_tok = int(details.get("image_tokens") or max(0, int(usage.get("input_tokens") or 0) - text_tok))
        out_tok = int(usage.get("output_tokens") or 0)
        if text_tok == 0 and img_tok == 0 and out_tok == 0:
            return float(spec["est_med"])
        usd = (
            (text_tok / 1_000_000.0) * float(spec["text_in"])
            + (img_tok / 1_000_000.0) * float(spec["img_in"])
            + (out_tok / 1_000_000.0) * float(spec["img_out"])
        )
        return round(usd, 6)

    def _default_offline_mock_responder(
        self, url: str, headers: Dict[str, str], content_type: str, body: bytes
    ) -> Tuple[int, Dict[str, str], bytes]:
        if not url.endswith("/images/edits"):
            return 404, {}, json.dumps({"error": {"message": "Bad URL", "code": "invalid_url"}}).encode("utf-8")
        if not headers.get("Authorization", "").startswith("Bearer "):
            return 401, {}, json.dumps({"error": {"message": "Missing Bearer", "code": "invalid_api_key"}}).encode("utf-8")
        if b'name="response_format"' in body:
            return 400, {}, json.dumps({"error": {"message": "Unknown parameter: response_format"}}).encode("utf-8")
        if self._model_name.startswith("gpt-image-2") and b'name="input_fidelity"' in body:
            return 400, {}, json.dumps({"error": {"message": "Unknown parameter: input_fidelity"}}).encode("utf-8")
        if body.count(b'name="image[]"') != 2:
            return 400, {}, json.dumps({"error": {"message": "Expected 2 image[] parts"}}).encode("utf-8")

        if self._mock_scenario == "insufficient_quota":
            return (
                429,
                {"x-request-id": "req_mock_quota"},
                json.dumps(
                    {
                        "error": {
                            "message": "You exceeded your current quota.",
                            "type": "insufficient_quota",
                            "code": "insufficient_quota",
                        }
                    }
                ).encode("utf-8"),
            )
        if self._mock_scenario == "invalid_api_key":
            return (
                401,
                {"x-request-id": "req_mock_401"},
                json.dumps({"error": {"message": "Incorrect API key sk-secret1234567890", "code": "invalid_api_key"}}).encode("utf-8"),
            )
        if self._mock_scenario == "network_timeout":
            raise TimeoutError("Simulated connection drop after POST /v1/images/edits")
        if self._mock_scenario == "empty_data":
            return 200, {"x-request-id": "req_mock_empty"}, json.dumps({"data": []}).encode("utf-8")

        out_png = base64.b64decode(_PNG_1X1_BASE64)
        payload = {
            "created": int(time.time()),
            "output_format": "png",
            "data": [{"b64_json": base64.b64encode(out_png).decode("ascii")}],
            "usage": {
                "total_tokens": 6210,
                "input_tokens": 2050,
                "output_tokens": 4160,
                "input_tokens_details": {"text_tokens": 210, "image_tokens": 1840},
            },
        }
        return 200, {"x-request-id": f"req_mock_openai_{self.call_count}"}, json.dumps(payload).encode("utf-8")

    def run_single_pass(self, call_input: ProviderCallInput) -> ProviderCallOutput:
        if not self._api_key:
            raise PocValidationError("UNAUTHORIZED", "OPENAI_API_KEY is not configured in environment.")
        if not _has_valid_raster_magic(call_input.person_image_bytes):
            raise PocValidationError("INVALID_REQUEST", "Person image bytes failed raster magic-byte check.")
        if not _has_valid_raster_magic(call_input.garment_image_bytes):
            raise PocValidationError("INVALID_REQUEST", "Garment image bytes failed raster magic-byte check.")

        prompt = build_openai_vto_prompt(call_input.view, call_input.garment_category)
        fields: List[Tuple[str, str]] = [
            ("model", self._model_name),
            ("prompt", prompt),
            ("n", "1"),
            ("size", self._size),
            ("quality", self._quality),
            ("output_format", self._output_format),
        ]
        spec = VERIFIED_OPENAI_MODELS.get(self._model_name)
        if spec and spec["fidelity"] == "low_or_high":
            fields.append(("input_fidelity", "high"))

        ext_map = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}
        p_ext = ext_map.get(call_input.person_mime, "png")
        g_ext = ext_map.get(call_input.garment_mime, "png")
        files = [
            ("image[]", f"person_{call_input.view.lower()}.{p_ext}", call_input.person_mime, call_input.person_image_bytes),
            ("image[]", f"garment_{call_input.view.lower()}.{g_ext}", call_input.garment_mime, call_input.garment_image_bytes),
        ]
        content_type, body_bytes = _encode_multipart_formdata(fields, files)
        if len(body_bytes) > MAX_WIRE_PAYLOAD_BYTES:
            raise PocValidationError("PAYLOAD_TOO_LARGE", "OpenAI multipart payload exceeds 12 MiB limit.")

        self.last_multipart_summary = {
            "model": self._model_name,
            "imageCount": len(files),
            "n": "1",
            "hasInputFidelity": any(k == "input_fidelity" for k, _ in fields),
            "hasForbiddenResponseFormat": any(k == "response_format" for k, _ in fields),
            "promptVersion": OPENAI_VTO_PROMPT_VERSION,
        }

        url = f"{self._base_url}/images/edits"
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": content_type,
        }

        # Single POST only — ZERO automatic retry on timeout/network drop to prevent double billing!
        self.call_count += 1
        try:
            if self._transport_fn is not None:
                status_code, resp_headers, raw_resp = self._transport_fn(url, headers, content_type, body_bytes)
            elif self._offline_mock:
                status_code, resp_headers, raw_resp = self._default_offline_mock_responder(
                    url, headers, content_type, body_bytes
                )
            else:
                req = urllib.request.Request(url, data=body_bytes, method="POST", headers=headers)
                try:
                    with urllib.request.urlopen(req, timeout=call_input.timeout_seconds) as resp:
                        status_code = resp.status
                        resp_headers = {k.lower(): v for k, v in resp.headers.items()}
                        raw_resp = resp.read()
                except urllib.error.HTTPError as http_exc:
                    status_code = http_exc.code
                    resp_headers = {k.lower(): v for k, v in http_exc.headers.items()} if http_exc.headers else {}
                    raw_resp = http_exc.read() if http_exc.fp else b"{}"
        except Exception as exc:
            return ProviderCallOutput(
                prediction_id="unknown_submission",
                status="TIMED_OUT",
                output_bytes=None,
                credits_used=0.0,
                error_code="UPSTREAM_TIMEOUT",
                error_message=sanitize_diagnostic(
                    f"Ambiguous connection drop/timeout during OpenAI POST (no retry performed): {exc}"
                ),
            )

        req_id = resp_headers.get("x-request-id") or f"openai_{call_input.case_id}_{self.call_count}"
        try:
            parsed = json.loads(raw_resp.decode("utf-8"))
        except Exception:
            parsed = {}

        if status_code >= 400:
            err_obj = parsed.get("error") if isinstance(parsed, dict) else {}
            if not isinstance(err_obj, dict):
                err_obj = {}
            err_code_raw = str(err_obj.get("code") or "").lower()
            err_type_raw = str(err_obj.get("type") or "").lower()
            err_msg_raw = sanitize_diagnostic(str(err_obj.get("message") or f"HTTP {status_code}"))

            if status_code == 402 or "insufficient_quota" in (err_code_raw, err_type_raw) or "exceeded your current quota" in err_msg_raw.lower():
                mapped_code = "INSUFFICIENT_QUOTA"
            elif status_code == 401 or err_code_raw == "invalid_api_key":
                mapped_code = "UNAUTHORIZED"
            elif status_code == 403 or "organization" in err_code_raw:
                mapped_code = "FORBIDDEN"
            elif status_code == 429:
                mapped_code = "RATE_LIMITED"
            elif status_code == 404 or err_code_raw == "model_not_found":
                mapped_code = "UNSUPPORTED_CAPABILITY"
            else:
                mapped_code = "UPSTREAM_ERROR" if status_code >= 500 else "INVALID_REQUEST"

            return ProviderCallOutput(
                prediction_id=req_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code=mapped_code,
                error_message=err_msg_raw,
            )

        data_list = parsed.get("data") if isinstance(parsed, dict) else None
        if not isinstance(data_list, list) or not data_list:
            return ProviderCallOutput(
                prediction_id=req_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="INVALID_AI_OUTPUT",
                error_message="OpenAI response missing non-empty data[] list.",
            )

        first_item = data_list[0] if isinstance(data_list[0], dict) else {}
        b64_json = first_item.get("b64_json")
        if not isinstance(b64_json, str) or not b64_json.strip():
            return ProviderCallOutput(
                prediction_id=req_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="INVALID_AI_OUTPUT",
                error_message="OpenAI response missing inline b64_json image.",
            )

        try:
            decoded_out = base64.b64decode(b64_json.strip(), validate=True)
        except Exception:
            return ProviderCallOutput(
                prediction_id=req_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="INVALID_AI_OUTPUT",
                error_message="Corrupted b64_json returned by OpenAI.",
            )

        if not _has_valid_raster_magic(decoded_out):
            return ProviderCallOutput(
                prediction_id=req_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="INVALID_AI_OUTPUT",
                error_message="Decoded OpenAI image failed PNG/JPEG/WebP header check.",
            )

        credits_usd = 0.0 if self._offline_mock else self._estimate_usd_from_usage(parsed.get("usage"))
        return ProviderCallOutput(
            prediction_id=req_id,
            status="SUCCEEDED",
            output_bytes=decoded_out,
            credits_used=credits_usd,
        )


class FakeVtoProviderAdapter:
    """Deterministic fake provider for offline POC verification and fault injection."""

    def __init__(
        self,
        fail_views: Optional[Set[str]] = None,
        timeout_views: Optional[Set[str]] = None,
        simulated_latency_ms: int = 5,
        supported_categories: Optional[Set[str]] = None,
    ) -> None:
        self._fail_views = fail_views or set()
        self._timeout_views = timeout_views or set()
        self._simulated_latency_ms = simulated_latency_ms
        self._supported_categories = frozenset(supported_categories or DEFAULT_SUPPORTED_CATEGORIES)
        self._counter = 0

    @property
    def provider_id(self) -> str:
        return "fake-provider"

    @property
    def model_version(self) -> str:
        return "fake-v2"

    @property
    def is_live(self) -> bool:
        return False

    @property
    def supports_native_paired_views(self) -> bool:
        return False

    @property
    def supported_categories(self) -> frozenset[str]:
        return self._supported_categories

    @property
    def max_garments_per_call(self) -> int:
        return 1

    def run_single_pass(self, call_input: ProviderCallInput) -> ProviderCallOutput:
        self._counter += 1
        if self._simulated_latency_ms > 0:
            time.sleep(self._simulated_latency_ms / 1000.0)
        pred_id = f"fake-pred-{call_input.case_id}-{call_input.view.lower()}-p{call_input.pass_index}-{self._counter}"

        if call_input.view in self._timeout_views:
            return ProviderCallOutput(
                prediction_id=pred_id,
                status="TIMED_OUT",
                output_bytes=None,
                credits_used=0.0,
                error_code="UPSTREAM_TIMEOUT",
                error_message="Simulated fake timeout",
            )

        if call_input.view in self._fail_views:
            return ProviderCallOutput(
                prediction_id=pred_id,
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="UPSTREAM_ERROR",
                error_message=f"Simulated runtime failure on view {call_input.view}",
            )

        png_bytes = base64.b64decode(_PNG_1X1_BASE64) + (
            f"|FAKE_VTO|{call_input.case_id}|{call_input.view}|pass={call_input.pass_index}|{call_input.garment_item_id}"
        ).encode("utf-8")
        return ProviderCallOutput(
            prediction_id=pred_id,
            status="SUCCEEDED",
            output_bytes=png_bytes,
            credits_used=0.0,
        )


class FashnProviderAdapter:
    """
    Live adapter for FASHN Prediction API (`POST /v1/run` + `GET /v1/status/{id}`).
    Uses `return_base64: True` so outputs are NEVER persisted on FASHN's 72h CDN.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model_name: str = "tryon-v1.6",
        mode: str = "balanced",
        poll_interval_sec: float = 2.0,
    ) -> None:
        self._api_key = api_key or os.environ.get("FASHN_API_KEY", "")
        self._model_name = model_name
        self._mode = mode
        self._poll_interval_sec = poll_interval_sec

    @property
    def provider_id(self) -> str:
        return "fashn-ai"

    @property
    def model_version(self) -> str:
        return f"{self._model_name}-{self._mode}-base64"

    @property
    def is_live(self) -> bool:
        return True

    @property
    def supports_native_paired_views(self) -> bool:
        return False

    @property
    def supported_categories(self) -> frozenset[str]:
        if self._model_name == "tryon-max":
            return frozenset({"TOP", "BOTTOM", "DRESS", "OUTERWEAR", "SHOES", "ACCESSORY"})
        return DEFAULT_SUPPORTED_CATEGORIES

    @property
    def max_garments_per_call(self) -> int:
        return 1

    def _category_for_fashn(self, category: str) -> str:
        mapping = {
            "TOP": "tops",
            "OUTERWEAR": "tops",
            "BOTTOM": "bottoms",
            "DRESS": "one-pieces",
        }
        return mapping.get(category, "auto")

    def run_single_pass(self, call_input: ProviderCallInput) -> ProviderCallOutput:
        if not self._api_key:
            raise PocValidationError("UNAUTHORIZED", "FASHN_API_KEY is not set in environment.")

        person_uri = f"data:{call_input.person_mime};base64," + base64.b64encode(call_input.person_image_bytes).decode("ascii")
        garment_uri = f"data:{call_input.garment_mime};base64," + base64.b64encode(call_input.garment_image_bytes).decode("ascii")

        if self._model_name == "tryon-max":
            inputs: Dict[str, Any] = {
                "model_image": person_uri,
                "product_image": garment_uri,
                "generation_mode": self._mode if self._mode in ("balanced", "quality") else "balanced",
                "resolution": "1k",
                "num_images": 1,
                "output_format": "png",
                "return_base64": True,
            }
        else:
            inputs = {
                "model_image": person_uri,
                "garment_image": garment_uri,
                "category": self._category_for_fashn(call_input.garment_category),
                "mode": self._mode,
                "num_samples": 1,
                "output_format": "png",
                "return_base64": True,
            }

        payload_bytes = json.dumps({"model_name": self._model_name, "inputs": inputs}).encode("utf-8")
        if len(payload_bytes) > MAX_WIRE_PAYLOAD_BYTES:
            raise PocValidationError("PAYLOAD_TOO_LARGE", "Encoded FASHN request exceeds 12 MiB wire limit.")

        deadline = time.monotonic() + call_input.timeout_seconds
        req = urllib.request.Request(
            "https://api.fashn.ai/v1/run",
            data=payload_bytes,
            method="POST",
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=min(15.0, call_input.timeout_seconds)) as resp:
                run_body = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            code = "RATE_LIMITED" if exc.code == 429 else "UPSTREAM_ERROR"
            return ProviderCallOutput(
                prediction_id="unassigned",
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code=code,
                error_message=f"HTTP {exc.code} during FASHN submit",
            )
        except Exception as exc:
            return ProviderCallOutput(
                prediction_id="unassigned",
                status="TIMED_OUT",
                output_bytes=None,
                credits_used=0.0,
                error_code="UPSTREAM_TIMEOUT",
                error_message=sanitize_diagnostic(str(exc)),
            )

        pred_id = str(run_body.get("id") or "")
        if not pred_id:
            return ProviderCallOutput(
                prediction_id="unassigned",
                status="FAILED",
                output_bytes=None,
                credits_used=0.0,
                error_code="UPSTREAM_ERROR",
                error_message="Missing prediction id from FASHN /v1/run",
            )

        backoff = self._poll_interval_sec
        while time.monotonic() < deadline:
            time.sleep(min(backoff, max(0.1, deadline - time.monotonic())))
            backoff = min(8.0, backoff * 2.0)
            status_req = urllib.request.Request(
                f"https://api.fashn.ai/v1/status/{pred_id}",
                method="GET",
                headers={"Authorization": f"Bearer {self._api_key}"},
            )
            try:
                with urllib.request.urlopen(status_req, timeout=15.0) as s_resp:
                    credits_hdr = s_resp.headers.get("x-fashn-credits-used", "0")
                    status_body = json.loads(s_resp.read().decode("utf-8"))
            except Exception as exc:
                return ProviderCallOutput(
                    prediction_id=pred_id,
                    status="TIMED_OUT",
                    output_bytes=None,
                    credits_used=0.0,
                    error_code="NETWORK",
                    error_message=sanitize_diagnostic(str(exc)),
                )

            st = status_body.get("status")
            if st == "completed":
                outputs = status_body.get("output") or []
                if not outputs:
                    return ProviderCallOutput(
                        prediction_id=pred_id,
                        status="FAILED",
                        output_bytes=None,
                        credits_used=float(credits_hdr or 0.0),
                        error_code="INVALID_AI_OUTPUT",
                        error_message="Completed status had empty output list",
                    )
                first_out = str(outputs[0])
                if ";base64," in first_out:
                    raw_b64 = first_out.split(";base64,", 1)[1]
                    out_bytes = base64.b64decode(raw_b64)
                else:
                    return ProviderCallOutput(
                        prediction_id=pred_id,
                        status="FAILED",
                        output_bytes=None,
                        credits_used=float(credits_hdr or 0.0),
                        error_code="INVALID_AI_OUTPUT",
                        error_message="Expected base64 output when return_base64=true",
                    )
                default_credits = 2.0 if self._model_name == "tryon-max" else 1.0
                used_credits = float(credits_hdr) if credits_hdr and float(credits_hdr) > 0 else default_credits
                return ProviderCallOutput(
                    prediction_id=pred_id,
                    status="SUCCEEDED",
                    output_bytes=out_bytes,
                    credits_used=used_credits,
                )
            if st == "failed":
                err_obj = status_body.get("error") or {}
                err_name = err_obj.get("name", "PipelineError")
                return ProviderCallOutput(
                    prediction_id=pred_id,
                    status="FAILED",
                    output_bytes=None,
                    credits_used=0.0,  # FASHN does not charge failed predictions
                    error_code="UPSTREAM_ERROR",
                    error_message=sanitize_diagnostic(f"{err_name}: {err_obj.get('message', '')}"),
                )

        return ProviderCallOutput(
            prediction_id=pred_id,
            status="TIMED_OUT",
            output_bytes=None,
            credits_used=0.0,
            error_code="UPSTREAM_TIMEOUT",
            error_message="Exceeded technical poll deadline",
        )


def apply_negative_mutation(base_case: Dict[str, Any], mutation: str, case_id: str) -> Dict[str, Any]:
    """Constructs negative test fixtures from a valid positive case without mutating the original."""
    mutated = copy.deepcopy(base_case)
    mutated["caseId"] = case_id
    if mutation == "REMOVE_PERSON_BACK":
        mutated["personImages"]["back"] = None
    elif mutation == "REMOVE_GARMENT_BACK":
        mutated["items"][0]["images"]["back"] = None
    elif mutation == "CLONE_FRONT_AS_BACK":
        # Violates CT-01/CT-06 rule: never duplicate front image and pretend it is back!
        cloned = copy.deepcopy(mutated["personImages"]["front"])
        mutated["personImages"]["back"] = cloned
    elif mutation == "DELETE_GARMENT_MEDIA":
        mutated["items"][0]["images"]["front"]["exists"] = False
    elif mutation == "BUMP_STORE_REVISION":
        mutated["items"][0]["currentStoreRevision"] = mutated["items"][0]["snapshotRevision"] + 1
    elif mutation == "SELECT_ONLY_UNSUPPORTED_ACCESSORY":
        mutated["items"] = [
            {
                "itemId": "item-acc-99",
                "label": "Kính mát gọng tròn",
                "category": "ACCESSORY",
                "snapshotRevision": 1,
                "currentStoreRevision": 1,
                "images": {
                    "front": {
                        "id": "media-acc99-front",
                        "ownerEntityId": "item-acc-99",
                        "scope": "GARMENT",
                        "view": "FRONT",
                        "mimeType": "image/png",
                        "width": 512,
                        "height": 512,
                        "byteCount": 20480,
                        "contentHash": "sha256-acc99-front",
                        "revision": 1,
                        "exists": true_bool(),
                    },
                    "back": None,
                },
            }
        ]
        mutated["selectedItemIds"] = ["item-acc-99"]
        mutated["consent"]["selectedMediaIds"].append("media-acc99-front")
    else:
        raise ValueError(f"Unknown mutation: {mutation}")
    return mutated


def true_bool() -> bool:
    return True


def _validate_media_ref(
    ref: Optional[Dict[str, Any]],
    expected_scope: str,
    expected_view: str,
    max_bytes: int,
    missing_label: str,
) -> Dict[str, Any]:
    if ref is None:
        raise PocValidationError("NEEDS_INPUT", f"Missing required {missing_label} ({expected_view}).")
    if not ref.get("exists", True):
        raise PocValidationError("STALE_INPUT", f"Media {ref.get('id')} for {missing_label} was deleted.")
    if ref.get("scope") != expected_scope:
        raise PocValidationError("INVALID_REQUEST", f"Invalid media scope on {ref.get('id')}: expected {expected_scope}.")
    if ref.get("view") != expected_view:
        raise PocValidationError(
            "INVALID_REQUEST",
            f"Wrong view pairing on {ref.get('id')}: slot requires {expected_view} but MediaRef is {ref.get('view')}.",
        )
    mime = ref.get("mimeType", "")
    if mime not in ALLOWED_MIMES:
        raise PocValidationError("INVALID_REQUEST", f"Unsupported MIME type {mime}.")
    byte_count = int(ref.get("byteCount", 0))
    if byte_count <= 0 or byte_count > max_bytes:
        raise PocValidationError(
            "PAYLOAD_TOO_LARGE",
            f"Media {ref.get('id')} byteCount={byte_count} violates cap {max_bytes}.",
        )
    return ref


def _load_or_synthesize_bytes(ref: Dict[str, Any], private_input_dir: Optional[Path]) -> bytes:
    rel_path = ref.get("localRelativePath")
    if private_input_dir and rel_path:
        candidate = (private_input_dir / rel_path).resolve()
        if not str(candidate).startswith(str(private_input_dir.resolve())):
            raise PocValidationError("FORBIDDEN", "Path traversal detected in localRelativePath.")
        if not candidate.exists():
            raise PocValidationError("STALE_INPUT", "Referenced local input file is missing.")
        data = candidate.read_bytes()
        return data
    # Deterministic synthetic PNG bytes for technical fixtures
    base = base64.b64decode(_PNG_1X1_BASE64)
    digest = hashlib.sha256(str(ref.get("contentHash", ref.get("id"))).encode("utf-8")).digest()
    return base + digest


def _build_rubric_scaffold(views: Sequence[str], requires_pair: bool) -> Dict[str, Any]:
    per_reviewer = {
        "identityPreservationFront": None,
        "silhouetteAndPose": None,
        "garmentFidelity": None,
        "logoPreservation": None,
        "textFidelity": None,
        "coverageAndProvenance": None,
        "pairConsistency": None if requires_pair else "N/A_SINGLE_VIEW",
        "notes": "Requires independent human scoring (1..5, PASS >= 4 on all applicable criteria).",
    }
    return {
        "qualityStatus": "UNREVIEWED",
        "reviewerC": copy.deepcopy(per_reviewer),
        "reviewerA": copy.deepcopy(per_reviewer),
    }


class PocHarnessRunner:
    """Executes C-203 (front) & C-204 (paired back / multi-garment probe) with strict safety gates."""

    def __init__(
        self,
        adapter: VtoProviderAdapter,
        budget: BudgetPolicy,
        private_output_dir: Path,
        private_input_dir: Optional[Path] = None,
    ) -> None:
        self._adapter = adapter
        self._budget = budget
        self._private_output_dir = private_output_dir
        self._private_input_dir = private_input_dir
        self._attempts_used = 0
        self._spend_usd = 0.0
        self._ensure_private_dir()

    def _ensure_private_dir(self) -> None:
        self._private_output_dir.mkdir(parents=True, exist_ok=True)
        os.chmod(self._private_output_dir, 0o700)

    def _check_live_gate(self, manifest_eligible_for_live: bool) -> None:
        if self._budget.kill_switch_active:
            raise PocValidationError("UNSUPPORTED_CAPABILITY", "VTO Kill Switch is active.")
        if self._adapter.is_live:
            if not self._budget.allow_live_calls:
                raise PocValidationError("FORBIDDEN", "Live API calls are disabled (missing --allow-live).")
            if not self._budget.pre001_hackathon_cleared:
                raise PocValidationError("FORBIDDEN", "PRE-001 hackathon live-POC clearance is not confirmed.")
            if not manifest_eligible_for_live:
                raise PocValidationError(
                    "CONSENT_REQUIRED",
                    "Synthetic technical fixtures cannot be sent to live paid VTO as human quality evidence.",
                )
            if self._budget.approved_budget_usd <= 0:
                raise PocValidationError("BUDGET_EXCEEDED", "Approved live VTO budget is $0.00.")

    def _validate_case_and_plan(
        self, case: Dict[str, Any], manifest_eligible_for_live: bool
    ) -> Tuple[List[str], List[Dict[str, Any]], List[str], List[str]]:
        self._check_live_gate(manifest_eligible_for_live)

        requested_views = list(case.get("requestedViews") or [])
        if not requested_views or any(v not in VALID_VIEWS for v in requested_views):
            raise PocValidationError("INVALID_REQUEST", f"Invalid requestedViews: {requested_views}")

        # Validate consent grant per CT-03 / CT-06
        consent = case.get("consent") or {}
        if not consent.get("confirmedByUser") or consent.get("revoked"):
            raise PocValidationError("CONSENT_REQUIRED", "Valid user/subject consent is required before VTO.")
        if consent.get("purpose") not in ("TRYON", "TRYON_POC_EVALUATION"):
            raise PocValidationError("CONSENT_REQUIRED", "Consent purpose does not authorize TRYON.")
        if consent.get("providerId") != self._adapter.provider_id:
            raise PocValidationError(
                "CONSENT_REQUIRED",
                f"Consent granted for {consent.get('providerId')}, cannot upload to {self._adapter.provider_id}.",
            )
        allowed_views = set(consent.get("allowedViews") or [])
        if not set(requested_views).issubset(allowed_views):
            raise PocValidationError("CONSENT_REQUIRED", "Requested views exceed consented views.")
        consented_media_ids = set(consent.get("selectedMediaIds") or [])

        # Validate outfit item revisions & partition supported vs unapplied categories
        items = case.get("items") or []
        if not items:
            raise PocValidationError("INVALID_REQUEST", "Outfit has no items.")

        selected_ids = set(case.get("selectedItemIds") or [it["itemId"] for it in items])
        supported_items: List[Dict[str, Any]] = []
        unapplied_ids: List[str] = []
        warnings: List[str] = []

        for item in items:
            item_id = item["itemId"]
            if int(item.get("snapshotRevision", 0)) != int(item.get("currentStoreRevision", 0)):
                raise PocValidationError(
                    "STALE_INPUT",
                    f"Outfit item {item_id} revision {item.get('snapshotRevision')} is stale.",
                )
            cat = item.get("category", "UNKNOWN")
            if item_id in selected_ids and cat in self._adapter.supported_categories:
                supported_items.append(item)
            else:
                unapplied_ids.append(item_id)
                warnings.append(f"Excluded unsupported/unselected item {item_id} ({cat}).")

        if not supported_items:
            raise PocValidationError(
                "UNSUPPORTED_CAPABILITY",
                "None of the selected outfit items are supported by the provider capability.",
            )

        # Validate person & garment MediaRefs for every requested view; NEVER substitute front for back
        person_imgs = case.get("personImages") or {}
        total_decoded_bytes = 0

        if "FRONT" in requested_views and "BACK" in requested_views:
            pf = person_imgs.get("front")
            pb = person_imgs.get("back")
            if pf and pb and pf.get("contentHash") == pb.get("contentHash"):
                raise PocValidationError(
                    "INVALID_REQUEST",
                    "Person front and back have identical contentHash (cloned front-as-back is forbidden).",
                )

        for view in requested_views:
            slot = "front" if view == "FRONT" else "back"
            p_ref = _validate_media_ref(
                person_imgs.get(slot),
                expected_scope="PERSON",
                expected_view=view,
                max_bytes=MAX_PERSON_VIEW_BYTES,
                missing_label="person photo",
            )
            if p_ref["id"] not in consented_media_ids:
                raise PocValidationError("CONSENT_REQUIRED", f"Person media {p_ref['id']} is outside consent scope.")
            total_decoded_bytes += int(p_ref["byteCount"])

            for item in supported_items:
                g_imgs = item.get("images") or {}
                if "FRONT" in requested_views and "BACK" in requested_views:
                    gf = g_imgs.get("front")
                    gb = g_imgs.get("back")
                    if gf and gb and gf.get("contentHash") == gb.get("contentHash"):
                        raise PocValidationError(
                            "INVALID_REQUEST",
                            f"Garment {item['itemId']} front and back have identical contentHash.",
                        )
                g_ref = _validate_media_ref(
                    g_imgs.get(slot),
                    expected_scope="GARMENT",
                    expected_view=view,
                    max_bytes=MAX_GARMENT_VIEW_BYTES,
                    missing_label=f"garment {item['itemId']}",
                )
                if g_ref["id"] not in consented_media_ids:
                    raise PocValidationError(
                        "CONSENT_REQUIRED",
                        f"Garment media {g_ref['id']} is outside consent scope.",
                    )
                total_decoded_bytes += int(g_ref["byteCount"])

        if total_decoded_bytes > MAX_TOTAL_DECODED_BYTES:
            raise PocValidationError(
                "PAYLOAD_TOO_LARGE",
                f"Total decoded bytes {total_decoded_bytes} exceed 8 MiB limit.",
            )

        return requested_views, supported_items, unapplied_ids, warnings

    def run_case(self, case: Dict[str, Any], manifest_eligible_for_live: bool = False) -> CaseEvaluationRecord:
        case_id = str(case.get("caseId", "unknown-case"))
        outfit_id = str(case.get("outfitId", "unknown-outfit"))

        try:
            requested_views, supported_items, unapplied_ids, warnings = self._validate_case_and_plan(
                case, manifest_eligible_for_live
            )
        except PocValidationError as exc:
            return CaseEvaluationRecord(
                case_id=case_id,
                outfit_id=outfit_id,
                requested_views=list(case.get("requestedViews") or []),
                pair_execution_mode="NONE",
                garment_application_mode="NONE",
                applied_item_ids=[],
                unapplied_item_ids=[it["itemId"] for it in (case.get("items") or [])],
                requires_sequential_drift_check=False,
                overall_status="REJECTED_PRE_UPLOAD",
                rejection_code=exc.code,
                rejection_message=exc.message,
                total_latency_ms=0,
                total_cost_usd=0.0,
            )

        is_dual_requested = set(requested_views) == {"FRONT", "BACK"}
        if is_dual_requested:
            pair_mode = (
                "NATIVE_PAIRED" if self._adapter.supports_native_paired_views else "TWO_INDEPENDENT_REQUESTS"
            )
            if not self._adapter.supports_native_paired_views:
                warnings.append(
                    "Provider does NOT support native paired generation; FRONT and BACK are executed as two independent requests and MUST pass human cross-view consistency review."
                )
        else:
            pair_mode = f"SINGLE_VIEW_{requested_views[0]}"

        is_sequential_multi = len(supported_items) > 1 and self._adapter.max_garments_per_call == 1
        garment_mode = "SEQUENTIAL_MULTI_GARMENT" if is_sequential_multi else "SINGLE_GARMENT"
        if is_sequential_multi:
            warnings.append(
                "Multiple garments applied sequentially across passes; reviewer MUST verify pass 2 did not overwrite or degrade pass 1 garment."
            )

        attempts: List[AttemptRecord] = []
        view_succeeded: Dict[str, bool] = {}
        view_applied_items: Dict[str, List[str]] = {v: [] for v in requested_views}
        case_start = time.monotonic()

        for view in requested_views:
            slot = "front" if view == "FRONT" else "back"
            person_ref = case["personImages"][slot]
            current_person_bytes = _load_or_synthesize_bytes(person_ref, self._private_input_dir)
            current_person_mime = person_ref["mimeType"]
            this_view_ok = True

            for pass_idx, item in enumerate(supported_items, start=1):
                if self._attempts_used >= self._budget.max_attempts:
                    this_view_ok = False
                    attempts.append(
                        AttemptRecord(
                            attempt_index=self._attempts_used + 1,
                            case_id=case_id,
                            view=view,
                            pass_index=pass_idx,
                            garment_item_id=item["itemId"],
                            provider_id=self._adapter.provider_id,
                            model_version=self._adapter.model_version,
                            source="AI_LIVE" if self._adapter.is_live else "FAKE",
                            prediction_id=None,
                            status="BLOCKED",
                            error_code="BUDGET_EXCEEDED",
                            error_message="Reached max_attempts cap.",
                            latency_ms=0,
                            credits_used=0.0,
                            estimated_cost_usd=0.0,
                            private_output_file=None,
                        )
                    )
                    break

                if self._adapter.is_live and self._spend_usd >= self._budget.approved_budget_usd:
                    this_view_ok = False
                    attempts.append(
                        AttemptRecord(
                            attempt_index=self._attempts_used + 1,
                            case_id=case_id,
                            view=view,
                            pass_index=pass_idx,
                            garment_item_id=item["itemId"],
                            provider_id=self._adapter.provider_id,
                            model_version=self._adapter.model_version,
                            source="AI_LIVE",
                            prediction_id=None,
                            status="BLOCKED",
                            error_code="BUDGET_EXCEEDED",
                            error_message="Reached approved USD budget cap.",
                            latency_ms=0,
                            credits_used=0.0,
                            estimated_cost_usd=0.0,
                            private_output_file=None,
                        )
                    )
                    break

                garment_ref = item["images"][slot]
                garment_bytes = _load_or_synthesize_bytes(garment_ref, self._private_input_dir)

                self._attempts_used += 1
                attempt_no = self._attempts_used
                t0 = time.monotonic()
                out = self._adapter.run_single_pass(
                    ProviderCallInput(
                        case_id=case_id,
                        view=view,
                        pass_index=pass_idx,
                        garment_item_id=item["itemId"],
                        garment_category=item["category"],
                        person_image_bytes=current_person_bytes,
                        person_mime=current_person_mime,
                        garment_image_bytes=garment_bytes,
                        garment_mime=garment_ref["mimeType"],
                        timeout_seconds=120.0 if not is_dual_requested else 90.0,
                    )
                )
                elapsed_ms = int((time.monotonic() - t0) * 1000)
                est_cost = round(out.credits_used * self._budget.cost_per_credit_usd, 4)
                self._spend_usd += est_cost

                saved_filename: Optional[str] = None
                if out.status == "SUCCEEDED" and out.output_bytes is not None:
                    saved_filename = f"{case_id}_{view.lower()}_pass{pass_idx}.png"
                    out_path = self._private_output_dir / saved_filename
                    out_path.write_bytes(out.output_bytes)
                    os.chmod(out_path, 0o600)
                    # Feed output of pass N into pass N+1 for sequential multi-garment probe
                    current_person_bytes = out.output_bytes
                    current_person_mime = "image/png"
                    view_applied_items[view].append(item["itemId"])
                else:
                    this_view_ok = False

                attempts.append(
                    AttemptRecord(
                        attempt_index=attempt_no,
                        case_id=case_id,
                        view=view,
                        pass_index=pass_idx,
                        garment_item_id=item["itemId"],
                        provider_id=self._adapter.provider_id,
                        model_version=self._adapter.model_version,
                        source="AI_LIVE" if self._adapter.is_live else "FAKE",
                        prediction_id=out.prediction_id,
                        status=out.status,
                        error_code=out.error_code,
                        error_message=out.error_message,
                        latency_ms=elapsed_ms,
                        credits_used=out.credits_used,
                        estimated_cost_usd=est_cost,
                        private_output_file=saved_filename,
                    )
                )
                if not this_view_ok:
                    break

            view_succeeded[view] = this_view_ok

        total_latency_ms = int((time.monotonic() - case_start) * 1000)
        total_cost = round(sum(a.estimated_cost_usd for a in attempts), 4)

        succeeded_count = sum(1 for ok in view_succeeded.values() if ok)
        if succeeded_count == len(requested_views):
            overall_status = "SUCCEEDED"
        elif succeeded_count > 0:
            overall_status = "PARTIAL"
            warnings.append("Partial completion: only a subset of requested views succeeded. Do NOT claim DUAL complete.")
        else:
            overall_status = "FAILED"

        # Applied items are only those applied across ALL succeeded views
        if succeeded_count > 0:
            succeeded_views = [v for v, ok in view_succeeded.items() if ok]
            common_applied = set(view_applied_items[succeeded_views[0]])
            for sv in succeeded_views[1:]:
                common_applied &= set(view_applied_items[sv])
            applied_ids = [it["itemId"] for it in supported_items if it["itemId"] in common_applied]
        else:
            applied_ids = []

        final_unapplied = list(unapplied_ids) + [
            it["itemId"] for it in supported_items if it["itemId"] not in applied_ids
        ]

        return CaseEvaluationRecord(
            case_id=case_id,
            outfit_id=outfit_id,
            requested_views=requested_views,
            pair_execution_mode=pair_mode,
            garment_application_mode=garment_mode,
            applied_item_ids=applied_ids,
            unapplied_item_ids=final_unapplied,
            requires_sequential_drift_check=is_sequential_multi,
            overall_status=overall_status,
            rejection_code=None,
            rejection_message=None,
            total_latency_ms=total_latency_ms,
            total_cost_usd=total_cost,
            warnings=warnings,
            attempts=attempts,
            rubric_template=_build_rubric_scaffold(requested_views, is_dual_requested),
        )


def purge_subject_private_outputs(private_output_dir: Path, subject_prefix: str) -> int:
    """Deletes private POC outputs matching a case/subject prefix."""
    if not private_output_dir.exists():
        return 0
    removed = 0
    for child in private_output_dir.iterdir():
        if child.is_file() and subject_prefix in child.name:
            child.unlink()
            removed += 1
    return removed


def _parse_tiff_exif_orientation(tiff_bytes: bytes) -> int:
    if len(tiff_bytes) < 8:
        return 1
    if tiff_bytes[:4] == b"II*\x00":
        endian = "little"
    elif tiff_bytes[:4] == b"MM\x00*":
        endian = "big"
    else:
        return 1
    ifd_offset = int.from_bytes(tiff_bytes[4:8], endian)
    if ifd_offset + 2 > len(tiff_bytes):
        return 1
    entry_count = int.from_bytes(tiff_bytes[ifd_offset : ifd_offset + 2], endian)
    for i in range(entry_count):
        off = ifd_offset + 2 + i * 12
        if off + 12 > len(tiff_bytes):
            break
        tag = int.from_bytes(tiff_bytes[off : off + 2], endian)
        if tag == 0x0112:
            val = int.from_bytes(tiff_bytes[off + 8 : off + 10], endian)
            return val if 1 <= val <= 8 else 1
    return 1


def inspect_local_image_file(file_path: Path) -> Dict[str, Any]:
    """
    Inspects magic bytes, dimensions (width, height), EXIF orientation (0x0112), byteCount, and SHA-256 contentHash
    of a local JPEG, PNG, or WebP image file without external library dependencies.
    """
    if not file_path.is_file():
        raise PocValidationError("NEEDS_INPUT", f"Missing local image file: {file_path.name}")
    data = file_path.read_bytes()
    byte_count = len(data)
    if byte_count < 24:
        raise PocValidationError("INVALID_REQUEST", f"File {file_path.name} is too small to be a valid image.")

    content_hash = "sha256-" + hashlib.sha256(data).hexdigest()[:32]
    width, height = 0, 0
    exif_orientation = 1
    mime_type = ""

    # PNG magic header + IHDR + IEND trailer
    if data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR" and b"IEND" in data:
        mime_type = "image/png"
        width = int.from_bytes(data[16:20], "big")
        height = int.from_bytes(data[20:24], "big")
        off = 8
        while off + 12 <= byte_count:
            c_len = int.from_bytes(data[off : off + 4], "big")
            c_type = data[off + 4 : off + 8]
            if off + 12 + c_len > byte_count:
                break
            if c_type == b"eXIf" and c_len >= 8:
                exif_orientation = _parse_tiff_exif_orientation(data[off + 8 : off + 8 + c_len])
            if c_type == b"IEND":
                break
            off += 12 + c_len
    # JPEG magic header + SOFn + optional APP1 Exif
    elif data[:2] == b"\xff\xd8":
        mime_type = "image/jpeg"
        idx = 2
        while idx + 4 <= byte_count:
            if data[idx] != 0xFF:
                idx += 1
                continue
            while idx < byte_count and data[idx] == 0xFF:
                idx += 1
            if idx >= byte_count:
                break
            marker = data[idx]
            idx += 1
            if marker in (0xD9, 0xDA):
                break
            if marker in (0xD8, 0x01) or (0xD0 <= marker <= 0xD7):
                continue
            if idx + 2 > byte_count:
                break
            seg_len = int.from_bytes(data[idx : idx + 2], "big")
            if seg_len < 2 or idx + seg_len > byte_count:
                break
            if marker == 0xE1 and seg_len >= 10 and data[idx + 2 : idx + 8] == b"Exif\x00\x00":
                exif_orientation = _parse_tiff_exif_orientation(data[idx + 8 : idx + seg_len])
            if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF) and seg_len >= 7:
                height = int.from_bytes(data[idx + 3 : idx + 5], "big")
                width = int.from_bytes(data[idx + 5 : idx + 7], "big")
            idx += seg_len
        if width <= 0 or height <= 0:
            raise PocValidationError("INVALID_REQUEST", f"Corrupted JPEG SOF header in {file_path.name}.")
    # WebP RIFF magic header
    elif data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        mime_type = "image/webp"
        chunk = data[12:16]
        if chunk == b"VP8 " and byte_count >= 30:
            width = int.from_bytes(data[26:28], "little") & 0x3FFF
            height = int.from_bytes(data[28:30], "little") & 0x3FFF
        elif chunk == b"VP8L" and byte_count >= 25:
            b0, b1, b2, b3 = data[21], data[22], data[23], data[24]
            width = 1 + (((b1 & 0x3F) << 8) | b0)
            height = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6))
        elif chunk == b"VP8X" and byte_count >= 30:
            width = 1 + int.from_bytes(data[24:27], "little")
            height = 1 + int.from_bytes(data[27:30], "little")
        if width <= 0 or height <= 0:
            raise PocValidationError("INVALID_REQUEST", f"Corrupted WebP header in {file_path.name}.")
    else:
        raise PocValidationError(
            "INVALID_REQUEST",
            f"Unsupported or corrupted image format in {file_path.name}; only valid JPEG, PNG, or WebP are allowed.",
        )

    if width <= 0 or height <= 0:
        raise PocValidationError("INVALID_REQUEST", f"Invalid dimensions in {file_path.name}.")

    swapped = 5 <= exif_orientation <= 8
    effective_width = height if swapped else width
    effective_height = width if swapped else height

    return {
        "mimeType": mime_type,
        "width": effective_width,
        "height": effective_height,
        "rawWidth": width,
        "rawHeight": height,
        "exifOrientation": exif_orientation,
        "byteCount": byte_count,
        "contentHash": content_hash,
        "localRelativePath": file_path.name,
    }


def _find_slot_file(folder: Path, prefixes: Sequence[str]) -> Optional[Path]:
    for prefix in prefixes:
        for ext in (".jpg", ".jpeg", ".png", ".webp", ".JPG", ".JPEG", ".PNG", ".WEBP"):
            candidate = folder / f"{prefix}{ext}"
            if candidate.is_file():
                return candidate
    return None


def build_manifest_from_private_folder(
    private_dir: Path,
    provider_id: str = "fake-provider",
    consent_confirmed: bool = False,
    garment_category: str = "TOP",
    allow_inside_repo_for_tests: bool = False,
) -> Dict[str, Any]:
    """
    Scans a user's private folder outside Git (containing `person_front.*`, optional `person_back.*`,
    `garment_front.*` / `top_front.*`, optional `garment_back.*` / `top_back.*`) and generates a
    CT-06/CT-07 compliant POC manifest ready for preflight or live execution.
    """
    resolved_dir = private_dir.resolve()
    repo_root = Path(__file__).resolve().parents[2]
    if not allow_inside_repo_for_tests and str(resolved_dir).startswith(str(repo_root)):
        raise PocValidationError(
            "FORBIDDEN",
            "Private photo directory MUST be located outside the Git repository (/home/lamb/Project/FitCheckAI).",
        )
    if not resolved_dir.is_dir():
        raise PocValidationError("NEEDS_INPUT", "Private input directory does not exist.")

    os.chmod(resolved_dir, 0o700)

    person_front_file = _find_slot_file(resolved_dir, ("person_front",))
    if person_front_file is None:
        raise PocValidationError(
            "NEEDS_INPUT",
            "Missing person_front.(jpg|png|webp) in private folder.",
        )
    person_back_file = _find_slot_file(resolved_dir, ("person_back",))

    garment_front_file = _find_slot_file(resolved_dir, ("garment_front", "top_front", "bottom_front", "dress_front"))
    if garment_front_file is None:
        raise PocValidationError(
            "NEEDS_INPUT",
            "Missing garment_front.(jpg|png|webp) (or top_front/bottom_front/dress_front) in private folder.",
        )
    garment_back_file = _find_slot_file(resolved_dir, ("garment_back", "top_back", "bottom_back", "dress_back"))

    pf_meta = inspect_local_image_file(person_front_file)
    gf_meta = inspect_local_image_file(garment_front_file)
    pb_meta = inspect_local_image_file(person_back_file) if person_back_file else None
    gb_meta = inspect_local_image_file(garment_back_file) if garment_back_file else None

    has_dual = pb_meta is not None and gb_meta is not None
    requested_views = ["FRONT", "BACK"] if has_dual else ["FRONT"]

    selected_media_ids = ["media-user-person-front", "media-user-garment-front"]
    person_images: Dict[str, Any] = {
        "front": {
            "id": "media-user-person-front",
            "ownerEntityId": "user-self-poc",
            "scope": "PERSON",
            "view": "FRONT",
            "revision": 1,
            "exists": True,
            **pf_meta,
        },
        "back": None,
    }
    garment_images: Dict[str, Any] = {
        "front": {
            "id": "media-user-garment-front",
            "ownerEntityId": "item-user-01",
            "scope": "GARMENT",
            "view": "FRONT",
            "revision": 1,
            "exists": True,
            **gf_meta,
        },
        "back": None,
    }

    if has_dual and pb_meta is not None and gb_meta is not None:
        person_images["back"] = {
            "id": "media-user-person-back",
            "ownerEntityId": "user-self-poc",
            "scope": "PERSON",
            "view": "BACK",
            "revision": 1,
            "exists": True,
            **pb_meta,
        }
        garment_images["back"] = {
            "id": "media-user-garment-back",
            "ownerEntityId": "item-user-01",
            "scope": "GARMENT",
            "view": "BACK",
            "revision": 1,
            "exists": True,
            **gb_meta,
        }
        selected_media_ids.extend(["media-user-person-back", "media-user-garment-back"])

    manifest = {
        "schemaVersion": "2.0.0",
        "datasetKind": "CONSENTED_USER_LOCAL_PRIVATE",
        "eligibleForLiveQualityGate": bool(consent_confirmed),
        "positiveCases": [
            {
                "caseId": "case-user-self-01",
                "outfitId": "outfit-user-self-01",
                "requestedViews": requested_views,
                "selectedItemIds": ["item-user-01"],
                "personImages": person_images,
                "items": [
                    {
                        "itemId": "item-user-01",
                        "label": f"User Garment ({garment_category})",
                        "category": garment_category,
                        "snapshotRevision": 1,
                        "currentStoreRevision": 1,
                        "images": garment_images,
                    }
                ],
                "consent": {
                    "grantId": "consent-user-self-01",
                    "purpose": "TRYON_POC_EVALUATION",
                    "providerId": provider_id,
                    "policyVersion": "v2026-11-11",
                    "confirmedByUser": bool(consent_confirmed),
                    "revoked": False,
                    "allowedViews": requested_views,
                    "selectedMediaIds": selected_media_ids,
                },
            }
        ],
        "negativeCases": [],
    }
    manifest_out = resolved_dir / "manifest.json"
    manifest_out.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    os.chmod(manifest_out, 0o600)
    return manifest


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="FitCheckAI Track C VTO POC Runner")
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path(__file__).parent / "fixtures" / "technical_fixtures_manifest.json",
    )
    parser.add_argument(
        "--private-in",
        type=Path,
        default=None,
        help="Private folder outside Git containing local person/garment photos referenced by localRelativePath.",
    )
    parser.add_argument(
        "--auto-manifest-from-folder",
        type=Path,
        default=None,
        help="Automatically inspect person_front.* / garment_front.* in this private folder and build manifest.json.",
    )
    parser.add_argument(
        "--consent-confirmed",
        action="store_true",
        help="Confirm explicit user consent when building a manifest via --auto-manifest-from-folder.",
    )
    parser.add_argument(
        "--garment-category",
        choices=["TOP", "BOTTOM", "DRESS", "OUTERWEAR"],
        default="TOP",
    )
    parser.add_argument(
        "--private-out",
        type=Path,
        default=Path.home() / ".fitcheckai_private" / "vto_poc_outputs",
    )
    parser.add_argument("--provider", choices=["fake", "fashn", "openai"], default="fake")
    parser.add_argument("--openai-model", type=str, default=None)
    parser.add_argument("--openai-quality", choices=["low", "medium", "high", "auto"], default="medium")
    parser.add_argument("--openai-size", type=str, default="1024x1536")
    parser.add_argument("--offline-mock-openai", action="store_true")
    parser.add_argument("--allow-live", action="store_true")
    parser.add_argument("--pre001-cleared", action="store_true")
    parser.add_argument("--max-attempts", type=int, default=None)
    parser.add_argument("--budget-usd", type=float, default=0.0)
    args = parser.parse_args(argv)

    if args.provider == "openai":
        adapter: VtoProviderAdapter = OpenAiImageEditProviderAdapter(
            model_name=args.openai_model,
            quality=args.openai_quality,
            size=args.openai_size,
            offline_mock=args.offline_mock_openai,
        )
        cost_per_unit = 1.0  # OpenAiImageEditProviderAdapter reports direct USD in credits_used
        default_attempts = 1 if adapter.is_live else 20
    elif args.provider == "fashn":
        adapter = FashnProviderAdapter()
        cost_per_unit = 0.075
        default_attempts = 20
    else:
        adapter = FakeVtoProviderAdapter(simulated_latency_ms=1)
        cost_per_unit = 0.075
        default_attempts = 20

    private_in_dir = args.private_in
    if args.auto_manifest_from_folder is not None:
        private_in_dir = args.auto_manifest_from_folder
        manifest = build_manifest_from_private_folder(
            private_dir=args.auto_manifest_from_folder,
            provider_id=adapter.provider_id,
            consent_confirmed=args.consent_confirmed,
            garment_category=args.garment_category,
        )
    else:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))

    budget = BudgetPolicy(
        kill_switch_active=os.environ.get("TRYON_KILL_SWITCH") == "1",
        allow_live_calls=args.allow_live,
        pre001_hackathon_cleared=args.pre001_cleared,
        max_attempts=args.max_attempts if args.max_attempts is not None else default_attempts,
        approved_budget_usd=args.budget_usd,
        cost_per_credit_usd=cost_per_unit,
    )
    runner = PocHarnessRunner(
        adapter=adapter,
        budget=budget,
        private_output_dir=args.private_out,
        private_input_dir=private_in_dir,
    )

    positive_by_id = {c["caseId"]: c for c in manifest.get("positiveCases", [])}
    records: List[Dict[str, Any]] = []

    for pos_case in manifest.get("positiveCases", []):
        rec = runner.run_case(pos_case, manifest_eligible_for_live=bool(manifest.get("eligibleForLiveQualityGate")))
        records.append(dataclasses.asdict(rec))

    for neg_spec in manifest.get("negativeCases", []):
        base = positive_by_id[neg_spec["basePositiveCaseId"]]
        mutated = apply_negative_mutation(base, neg_spec["mutation"], neg_spec["caseId"])
        rec = runner.run_case(mutated, manifest_eligible_for_live=False)
        records.append(dataclasses.asdict(rec))

    report_path = args.private_out / "poc_evaluation_manifest.json"
    report_path.write_text(json.dumps({"provider": adapter.provider_id, "records": records}, indent=2), encoding="utf-8")
    os.chmod(report_path, 0o600)
    print(f"Completed {len(records)} POC cases (provider={adapter.provider_id}, live={adapter.is_live}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
