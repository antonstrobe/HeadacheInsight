# Implementation Plan

## Phases

1. Phase 0: repo bootstrap, docs, scripts, CI, env templates.
2. Phase 1: Android multi-module scaffold, design system, navigation, Room, DataStore, DI.
3. Phase 2: onboarding, home, quick log, episodes, history, red flags, local insights.
4. Phase 3: audio capture, local ASR abstraction, queued transcription, transcript persistence.
5. Phase 4: FastAPI backend, request signing, OpenAI proxy endpoints, schemas, prompts.
6. Phase 5: question engine, seed bank, cloud-generated questions, attachments, sync queue.
7. Phase 6: insights, reports, PDF/CSV/JSON export, share flows.
8. Phase 7: tests, hardening, CI, release docs, verification.

## Delivery Targets

- Android project builds from CLI with bootstrap scripts.
- Backend starts locally from CLI.
- Quick Log flow works offline with local persistence.
- Cloud analysis can be enabled without storing OpenAI credentials on device.
- Safety escalation works fully offline.
- Seed bank ships with RU/EN question templates and schema validation.
