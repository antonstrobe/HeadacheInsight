from typing import Literal

from pydantic import BaseModel, Field


class ClientRegisterRequest(BaseModel):
    install_id: str
    public_key: str
    device_locale: str
    device_timezone: str


class ClientRegisterResponse(BaseModel):
    client_id: str
    accepted: bool


class AnalyzeEpisodeRequest(BaseModel):
    owner_id: str
    schema_version: str = "v1"
    locale: str
    episode: dict
    include_follow_up_questions: bool = True


class GenerateFollowUpQuestionsRequest(BaseModel):
    owner_id: str
    locale: str
    episode: dict


class AnalyzeAttachmentsRequest(BaseModel):
    owner_id: str
    locale: str
    attachments: list[dict]


class TranscribeMetadata(BaseModel):
    episode_id: str
    locale: str
    language_hint: str | None = None


class TranscribeResponse(BaseModel):
    episode_id: str
    language: str | None = None
    transcript_text: str
    confidence: float | None = None
    engine_type: str


class GenerateFollowUpQuestionsResponse(BaseModel):
    schema_version: str = "v1"
    questions: list[dict]


class AnalyzeAttachmentsResponse(BaseModel):
    owner_id: str
    summary: list[str]


class HealthResponse(BaseModel):
    status: Literal["ok"] = "ok"
    service: str = "headacheinsight-backend"
