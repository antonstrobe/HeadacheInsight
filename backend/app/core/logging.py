import json
import logging
from typing import Any


SENSITIVE_KEYS = {"authorization", "openai_api_key", "api_key", "signature", "public_key", "requestPayloadJson"}


def redact(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: ("***" if key.lower() in SENSITIVE_KEYS else redact(item)) for key, item in value.items()}
    if isinstance(value, list):
        return [redact(item) for item in value]
    if isinstance(value, str) and len(value) > 120:
        return value[:24] + "...redacted..." + value[-8:]
    return value


def configure_logging(level: str) -> None:
    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def structured_log(logger: logging.Logger, message: str, **payload: Any) -> None:
    logger.info("%s %s", message, json.dumps(redact(payload), ensure_ascii=False))
