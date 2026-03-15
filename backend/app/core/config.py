from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    openai_api_key: str = "sk-placeholder"
    openai_analysis_model: str = "gpt-4.1"
    openai_question_model: str = "gpt-4.1-mini"
    openai_transcribe_model: str = "gpt-4o-transcribe"
    database_url: str = "sqlite:///./headacheinsight.db"
    rate_limit_per_minute: int = 60
    rate_limit_burst: int = 120
    log_level: str = "INFO"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def schema_dir(self) -> Path:
        return Path(__file__).resolve().parents[2] / "schemas" / "v1"

    @property
    def prompt_dir(self) -> Path:
        return Path(__file__).resolve().parents[2] / "prompts" / "v1"


settings = Settings()
