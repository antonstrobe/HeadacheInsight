import json
import logging
import uuid

from fastapi import Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from sqlalchemy.exc import IntegrityError

from app.core.config import settings
from app.core.logging import configure_logging, structured_log
from app.db.session import Base, engine, session_scope
from app.models.contracts import (
    AnalyzeAttachmentsRequest,
    AnalyzeAttachmentsResponse,
    AnalyzeEpisodeRequest,
    ClientRegisterRequest,
    ClientRegisterResponse,
    GenerateFollowUpQuestionsRequest,
    GenerateFollowUpQuestionsResponse,
    HealthResponse,
    TranscribeMetadata,
    TranscribeResponse,
)
from app.models.store import AuditLog, InstallClient
from app.services.openai_service import OpenAIRequestOverrides, OpenAIService
from app.services.security import verify_signed_request

configure_logging(settings.log_level)
logger = logging.getLogger("headacheinsight.backend")
app = FastAPI(title="HeadacheInsight Backend", version="0.1.0")
service = OpenAIService()
Base.metadata.create_all(bind=engine)


def record_audit(trace_id: str, path: str, method: str, success: bool, message: str, install_id: str | None = None) -> None:
    with session_scope() as session:
        session.add(
            AuditLog(
                trace_id=trace_id,
                install_id=install_id,
                path=path,
                method=method,
                success=success,
                message=message,
            ),
        )


@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    trace_id = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    request.state.trace_id = trace_id
    response = await call_next(request)
    response.headers["X-Correlation-Id"] = trace_id
    return response


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse()


@app.post("/api/client/register", response_model=ClientRegisterResponse)
def register_client(payload: ClientRegisterRequest, request: Request) -> ClientRegisterResponse:
    with session_scope() as session:
        session.merge(
            InstallClient(
                install_id=payload.install_id,
                public_key=payload.public_key,
                locale=payload.device_locale,
                timezone=payload.device_timezone,
            ),
        )
    record_audit(request.state.trace_id, request.url.path, request.method, True, "client-registered", payload.install_id)
    return ClientRegisterResponse(client_id=payload.install_id, accepted=True)


def require_signature(request: Request, body: bytes) -> str:
    try:
        return verify_signed_request(request, body)
    except Exception as exc:
        record_audit(request.state.trace_id, request.url.path, request.method, False, str(exc))
        raise


def request_openai_overrides(request: Request) -> OpenAIRequestOverrides:
    def read(name: str) -> str | None:
        value = request.headers.get(name)
        return value.strip() if value and value.strip() else None

    return OpenAIRequestOverrides(
        api_key=read("X-OpenAI-Api-Key"),
        analysis_model=read("X-OpenAI-Analysis-Model"),
        question_model=read("X-OpenAI-Question-Model"),
        transcribe_model=read("X-OpenAI-Transcribe-Model"),
    )


@app.post("/api/analyze-episode")
async def analyze_episode(request: Request) -> JSONResponse:
    body = await request.body()
    install_id = require_signature(request, body)
    payload = AnalyzeEpisodeRequest.model_validate_json(body)
    response = service.analyze_episode(payload.model_dump(), overrides=request_openai_overrides(request))
    record_audit(request.state.trace_id, request.url.path, request.method, True, "analysis-complete", install_id)
    structured_log(logger, "analysis_complete", trace_id=request.state.trace_id, install_id=install_id, owner_id=payload.owner_id)
    return JSONResponse(response)


@app.post("/api/generate-follow-up-questions", response_model=GenerateFollowUpQuestionsResponse)
async def generate_follow_up_questions(request: Request) -> GenerateFollowUpQuestionsResponse:
    body = await request.body()
    install_id = require_signature(request, body)
    payload = GenerateFollowUpQuestionsRequest.model_validate_json(body)
    response = service.generate_follow_up_questions(payload.model_dump(), overrides=request_openai_overrides(request))
    record_audit(request.state.trace_id, request.url.path, request.method, True, "questions-generated", install_id)
    return GenerateFollowUpQuestionsResponse.model_validate(response)


@app.post("/api/analyze-attachments", response_model=AnalyzeAttachmentsResponse)
async def analyze_attachments(request: Request) -> AnalyzeAttachmentsResponse:
    body = await request.body()
    install_id = require_signature(request, body)
    payload = AnalyzeAttachmentsRequest.model_validate_json(body)
    response = service.analyze_attachments(payload.model_dump(), overrides=request_openai_overrides(request))
    record_audit(request.state.trace_id, request.url.path, request.method, True, "attachments-analyzed", install_id)
    return AnalyzeAttachmentsResponse.model_validate(response)


@app.post("/api/transcribe", response_model=TranscribeResponse)
async def transcribe(
    request: Request,
    metadata: str = Form(...),
    file: UploadFile = File(...),
) -> TranscribeResponse:
    raw_body = await request.body()
    install_id = request.headers.get("X-Install-Id")
    if install_id:
        try:
            verify_signed_request(request, raw_body)
        except Exception as exc:
            record_audit(request.state.trace_id, request.url.path, request.method, False, str(exc), install_id)
            raise
    parsed_metadata = TranscribeMetadata.model_validate_json(metadata)
    content = await file.read()
    transcript = service.transcribe(file.filename or "audio.wav", content, overrides=request_openai_overrides(request))
    record_audit(request.state.trace_id, request.url.path, request.method, True, "transcription-complete", install_id)
    return TranscribeResponse(
        episode_id=parsed_metadata.episode_id,
        transcript_text=transcript["transcript_text"],
        language=transcript.get("language"),
        confidence=None,
        engine_type="cloud",
    )
