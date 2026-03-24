import json
from dataclasses import dataclass
from typing import Any

from openai import OpenAI

from app.core.config import settings
from app.services.prompt_loader import load_prompt
from app.services.schema_loader import load_schema


@dataclass(slots=True)
class OpenAIRequestOverrides:
    api_key: str | None = None
    analysis_model: str | None = None
    question_model: str | None = None
    transcribe_model: str | None = None


class OpenAIService:
    def __init__(self) -> None:
        self.analysis_prompt = load_prompt(settings.prompt_dir / "analysis_system.md")
        self.questions_prompt = load_prompt(settings.prompt_dir / "follow_up_system.md")
        self.attachments_prompt = load_prompt(settings.prompt_dir / "attachments_system.md")
        self.voice_intake_prompt = load_prompt(settings.prompt_dir / "voice_intake_system.md")
        self.analysis_schema = load_schema(settings.schema_dir / "analysis_response.schema.json")
        self.question_schema = load_schema(settings.schema_dir / "question_template_list.schema.json")
        self.attachments_schema = load_schema(settings.schema_dir / "attachment_analysis.schema.json")
        self.voice_intake_schema = load_schema(settings.schema_dir / "voice_intake_draft.schema.json")

    def _client(self, overrides: OpenAIRequestOverrides | None = None) -> OpenAI:
        api_key = overrides.api_key if overrides and overrides.api_key else settings.openai_api_key
        return OpenAI(api_key=api_key)

    def _json_response(
        self,
        *,
        model: str,
        prompt: str,
        schema_name: str,
        schema: dict,
        payload: dict,
        overrides: OpenAIRequestOverrides | None = None,
    ) -> dict:
        response = self._client(overrides).responses.create(
            model=model,
            input=[
                {"role": "system", "content": [{"type": "input_text", "text": prompt}]},
                {"role": "user", "content": [{"type": "input_text", "text": json.dumps(payload, ensure_ascii=False)}]},
            ],
            text={
                "format": {
                    "type": "json_schema",
                    "name": schema_name,
                    "schema": schema,
                    "strict": True,
                }
            },
        )
        return json.loads(response.output_text)

    def analyze_episode(self, payload: dict, overrides: OpenAIRequestOverrides | None = None) -> dict:
        return self._json_response(
            model=overrides.analysis_model if overrides and overrides.analysis_model else settings.openai_analysis_model,
            prompt=self.analysis_prompt,
            schema_name="analysis_response",
            schema=self.analysis_schema,
            payload=payload,
            overrides=overrides,
        )

    def generate_follow_up_questions(self, payload: dict, overrides: OpenAIRequestOverrides | None = None) -> dict:
        return self._json_response(
            model=overrides.question_model if overrides and overrides.question_model else settings.openai_question_model,
            prompt=self.questions_prompt,
            schema_name="question_template_list",
            schema=self.question_schema,
            payload=payload,
            overrides=overrides,
        )

    def analyze_attachments(self, payload: dict, overrides: OpenAIRequestOverrides | None = None) -> dict:
        return self._json_response(
            model=overrides.question_model if overrides and overrides.question_model else settings.openai_question_model,
            prompt=self.attachments_prompt,
            schema_name="attachment_analysis",
            schema=self.attachments_schema,
            payload=payload,
            overrides=overrides,
        )

    def voice_intake_draft(self, payload: dict, overrides: OpenAIRequestOverrides | None = None) -> dict:
        return self._json_response(
            model=overrides.analysis_model if overrides and overrides.analysis_model else settings.openai_analysis_model,
            prompt=self.voice_intake_prompt,
            schema_name="voice_intake_draft",
            schema=self.voice_intake_schema,
            payload=payload,
            overrides=overrides,
        )

    def transcribe(self, filename: str, content: bytes, overrides: OpenAIRequestOverrides | None = None) -> dict:
        from io import BytesIO

        file_like = BytesIO(content)
        file_like.name = filename
        transcript = self._client(overrides).audio.transcriptions.create(
            model=overrides.transcribe_model if overrides and overrides.transcribe_model else settings.openai_transcribe_model,
            file=file_like,
        )
        transcript_text = getattr(transcript, "text", str(transcript))
        language = getattr(transcript, "language", None)
        return {
            "transcript_text": transcript_text,
            "language": language,
        }
