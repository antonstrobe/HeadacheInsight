# Architecture

## Android

The Android app uses a strict multi-module architecture:

- `app`: entry point, navigation shell, Hilt application, worker wiring.
- `core/common`: platform-agnostic utilities and time/location/network abstractions.
- `core/model`: shared domain models, enums, and serialization contracts.
- `core/designsystem`: low-stimulation theme, tokens, reusable components.
- `core/ui`: shared Compose widgets and state containers.
- `core/testing`: fakes, test rules, fixtures, coroutine helpers.
- `data/local`: Room, DataStore, storage, mappers, seed loading.
- `data/remote`: backend DTOs, Retrofit/OkHttp clients, signing, schema adapters.
- `data/repository`: repository implementations and orchestration helpers.
- `domain`: repository interfaces, use cases, rule engines, export coordinators.
- `feature/*`: isolated UI features driven by ViewModels and use cases.

### Data Flow

1. UI emits intents/events to feature ViewModels.
2. ViewModels invoke domain use cases.
3. Use cases orchestrate repositories.
4. Repositories read/write Room and enqueue WorkManager jobs for remote sync.
5. Room remains the primary source of truth for structured local data.

## Backend

The backend is a thin FastAPI proxy with:

- install-scoped client registration
- request signature verification
- strict Pydantic request/response schemas
- redacted logs and correlation IDs
- OpenAI SDK integration for transcription and structured analysis

### Cloud Boundaries

- Android never holds the OpenAI API key.
- Android only calls the project backend over HTTPS.
- All LLM outputs are schema-driven JSON or bounded transcription results.
- Chain-of-thought is neither requested nor stored.
