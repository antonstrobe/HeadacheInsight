# Architecture Decisions

## ADR-001: Multi-module Android architecture

- Status: accepted
- Decision: use isolated feature modules, separate domain/data/core modules, and Hilt for DI.
- Rationale: aligns with modern Android references, supports scalable ownership, avoids a god module.

## ADR-002: Local-first storage strategy

- Status: accepted
- Decision: use Room as the primary source of truth for structured data and DataStore only for typed preferences/consents.
- Rationale: deterministic offline behavior, explicit migrations, strong testing support.

## ADR-003: Backend-routed OpenAI access with user-managed app keys

- Status: accepted
- Decision: all OpenAI calls still route through a FastAPI backend, but the Android app may securely store a user-provided OpenAI key in Keystore-backed isolated storage and forward it to the backend per request.
- Rationale: preserves the backend proxy boundary, keeps schema validation and redaction centralized, and supports the product requirement that end users can manage their own key directly inside the app without forcing backend-side secret storage.

## ADR-004: Local ASR engine choice

- Status: accepted
- Decision: use `sherpa-onnx` as the primary local ASR integration behind `SpeechRecognizerEngine`.
- Rationale: active Android support, Kotlin/Java integration surface, practical model provisioning strategy, and lower operational friction than shipping a custom `whisper.cpp` stack as the default.

## ADR-005: Security posture without user accounts

- Status: accepted
- Decision: use install-scoped Ed25519 identities, signed requests, anti-replay nonces, and backend rate limiting from MVP.
- Rationale: preserves a safe public-ready foundation without forcing auth/account scope into the first release.

## ADR-006: Local safety rules

- Status: accepted
- Decision: implement red-flag escalation as a deterministic local engine in the domain layer.
- Rationale: safety behavior must remain available offline and independent from cloud analysis latency.

## ADR-007: Structured cloud analysis outputs

- Status: accepted
- Decision: use Responses API structured JSON for analysis and follow-up questions, plus audio transcription endpoint for raw audio.
- Rationale: schema adherence reduces parsing failure and simplifies long-term contract evolution.

## ADR-008: Storage encryption posture

- Status: accepted
- Decision: use Android Keystore for signing keys, user-managed API keys, and encrypted attachment files; keep Room sandboxed but unencrypted in MVP.
- Rationale: delivers strong protection for secrets and attachments now, while keeping Room migration complexity lower until a stronger threat model requires SQLCipher.

## ADR-009: Language selection and Russian-first startup

- Status: accepted
- Decision: ship Russian and English from MVP, show a dedicated first-start language selector with two large buttons, and persist the chosen app locale independently from the device locale.
- Rationale: the acute-use entry flow must remain simple, one-handed, and immediately understandable for the target audience. A two-option selector is lower-risk than a full language wheel in the first release.

## ADR-010: Canonical JSON snapshot without replacing Room

- Status: accepted
- Decision: keep Room as the primary source of truth for structured local data, while also generating a canonical JSON snapshot/export bundle for portability, backend exchange, and clinician/report workflows.
- Rationale: JSON is useful for transport, export, debugging, and future integrations, but replacing Room with a JSON-only persistence model would weaken queries, migrations, offline reliability, and testability.

## ADR-011: Single source icon master asset

- Status: accepted
- Decision: keep one repository-level transparent master PNG in `img/app-icon-master.png` and generate Android launcher assets from it through scripts.
- Rationale: this avoids hand-editing many launcher resources, keeps icon updates reproducible, and makes future brand iterations safer across densities and adaptive icon surfaces.
