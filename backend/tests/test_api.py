import base64
import hashlib
import json
import time
import uuid
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi.testclient import TestClient

from app.main import app, service


def sign_request(private_key: Ed25519PrivateKey, method: str, path: str, body: bytes, timestamp: str, nonce: str) -> tuple[str, str]:
    body_hash = base64.b64encode(hashlib.sha256(body).digest()).decode()
    payload = f"{method}:{path}:{timestamp}:{nonce}:{body_hash}".encode()
    signature = base64.b64encode(private_key.sign(payload)).decode()
    return body_hash, signature


def register_client(client: TestClient, install_id: str, public_key_b64: str) -> None:
    response = client.post(
        "/api/client/register",
        json={
            "install_id": install_id,
            "public_key": public_key_b64,
            "device_locale": "ru-RU",
            "device_timezone": "Europe/Moscow",
        },
    )
    assert response.status_code == 200


def test_health() -> None:
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_connection_test_echoes_models() -> None:
    client = TestClient(app)
    response = client.get(
        "/api/connection-test",
        headers={
            "X-OpenAI-Api-Key": "sk-test",
            "X-OpenAI-Analysis-Model": "gpt-analysis",
            "X-OpenAI-Question-Model": "gpt-question",
            "X-OpenAI-Transcribe-Model": "gpt-transcribe",
        },
    )
    assert response.status_code == 200
    assert response.json()["api_key_present"] is True
    assert response.json()["analysis_model"] == "gpt-analysis"
    assert response.json()["question_model"] == "gpt-question"
    assert response.json()["transcribe_model"] == "gpt-transcribe"


def test_register_and_signed_analysis(monkeypatch) -> None:
    sample_payload = json.loads((Path(__file__).parent / "fixtures" / "sample_episode_request.json").read_text(encoding="utf-8"))
    private_key = Ed25519PrivateKey.generate()
    public_key_b64 = base64.b64encode(private_key.public_key().public_bytes_raw()).decode()
    install_id = f"install-test-{uuid.uuid4()}"
    client = TestClient(app)
    register_client(client, install_id, public_key_b64)

    monkeypatch.setattr(service, "analyze_episode", lambda payload, overrides=None: {
        "schema_version": "v1",
        "analysis_id": "analysis-1",
        "owner_type": "EPISODE",
        "owner_id": payload["owner_id"],
        "generated_at": "2026-03-15T10:05:00Z",
        "urgent_action": {"level": "MONITOR", "reasons": [], "user_message": "Monitor and document."},
        "user_summary": {"plain_language_summary": "Possible migraine-like pattern.", "key_observations": ["light sensitivity"]},
        "clinician_summary": {
            "concise_medical_context": "Unilateral throbbing headache.",
            "headache_day_estimate": "unknown",
            "acute_medication_use_estimate": "unknown",
            "functional_impact_summary": "moderate"
        },
        "hypotheses": [],
        "suspected_patterns": [],
        "next_questions": [],
        "suggested_tracking_fields": ["sleep"],
        "suggested_attachments": [],
        "suggested_doctor_discussion_points": ["Discuss recurrence pattern"],
        "suggested_specialists": [],
        "suggested_tests_or_evaluations": [],
        "self_care_general": ["Rest in a quiet room if needed."],
        "disclaimer": "Not a diagnosis.",
        "needs_human_clinician_review": True
    })

    body = json.dumps(sample_payload).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = f"nonce-{uuid.uuid4()}"
    body_hash, signature = sign_request(private_key, "POST", "/api/analyze-episode", body, timestamp, nonce)
    response = client.post(
        "/api/analyze-episode",
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Install-Id": install_id,
            "X-Timestamp": timestamp,
            "X-Nonce": nonce,
            "X-Body-SHA256": body_hash,
            "X-Signature": signature,
        },
    )
    assert response.status_code == 200
    assert response.json()["analysis_id"] == "analysis-1"


def test_signed_voice_intake_draft(monkeypatch) -> None:
    private_key = Ed25519PrivateKey.generate()
    public_key_b64 = base64.b64encode(private_key.public_key().public_bytes_raw()).decode()
    install_id = f"install-test-{uuid.uuid4()}"
    client = TestClient(app)
    register_client(client, install_id, public_key_b64)

    monkeypatch.setattr(service, "voice_intake_draft", lambda payload, overrides=None: {
        "schema_version": "v1",
        "owner_id": payload["owner_id"],
        "transcript_text": payload["transcript_text"],
        "summary_text": "Strong headache with nausea.",
        "severity": 8,
        "symptoms": ["Nausea", "Light sensitivity"],
        "red_flags": [],
        "medications": ["Ibuprofen"],
        "live_notes": ["Strong headache", "Nausea"],
        "dynamic_fields": [{"section": "symptoms", "label": "Nausea", "value": "detected"}],
        "engine_name": "cloud-openai",
    })

    body = json.dumps(
        {
            "owner_id": "episode-voice-1",
            "locale": "ru-RU",
            "transcript_text": "Сильно болит голова и тошнит.",
        },
    ).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = f"nonce-{uuid.uuid4()}"
    body_hash, signature = sign_request(private_key, "POST", "/api/voice-intake-draft", body, timestamp, nonce)
    response = client.post(
        "/api/voice-intake-draft",
        content=body,
        headers={
            "Content-Type": "application/json",
            "X-Install-Id": install_id,
            "X-Timestamp": timestamp,
            "X-Nonce": nonce,
            "X-Body-SHA256": body_hash,
            "X-Signature": signature,
        },
    )
    assert response.status_code == 200
    assert response.json()["summary_text"] == "Strong headache with nausea."
    assert response.json()["medications"] == ["Ibuprofen"]
