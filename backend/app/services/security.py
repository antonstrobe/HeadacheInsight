import base64
import hashlib
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
from fastapi import HTTPException, Request
from sqlalchemy import select

from app.core.config import settings
from app.db.session import session_scope
from app.models.store import InstallClient, ReplayNonce


class RateLimiter:
    def __init__(self) -> None:
        self.events: dict[str, deque[datetime]] = defaultdict(deque)

    def check(self, key: str) -> None:
        now = datetime.now(timezone.utc)
        window_start = now - timedelta(minutes=1)
        queue = self.events[key]
        while queue and queue[0] < window_start:
            queue.popleft()
        if len(queue) >= settings.rate_limit_per_minute:
            raise HTTPException(status_code=429, detail="Rate limit exceeded")
        queue.append(now)


rate_limiter = RateLimiter()


def verify_signed_request(request: Request, body: bytes) -> str:
    install_id = request.headers.get("X-Install-Id")
    timestamp = request.headers.get("X-Timestamp")
    nonce = request.headers.get("X-Nonce")
    body_hash = request.headers.get("X-Body-SHA256")
    signature = request.headers.get("X-Signature")
    if not all([install_id, timestamp, nonce, body_hash, signature]):
        raise HTTPException(status_code=401, detail="Missing signature headers")

    rate_limiter.check(install_id)
    expected_hash = base64.b64encode(hashlib.sha256(body).digest()).decode()
    if expected_hash != body_hash:
        raise HTTPException(status_code=401, detail="Body hash mismatch")

    ts = datetime.fromtimestamp(int(timestamp) / 1000, tz=timezone.utc)
    if abs((datetime.now(timezone.utc) - ts).total_seconds()) > 300:
        raise HTTPException(status_code=401, detail="Timestamp outside allowed skew")

    payload = f"{request.method}:{request.url.path}:{timestamp}:{nonce}:{body_hash}".encode()
    with session_scope() as session:
        client = session.get(InstallClient, install_id)
        if client is None:
            raise HTTPException(status_code=401, detail="Unknown install ID")

        existing = session.execute(
            select(ReplayNonce).where(ReplayNonce.install_id == install_id, ReplayNonce.nonce == nonce),
        ).scalar_one_or_none()
        if existing is not None:
            raise HTTPException(status_code=401, detail="Replay nonce detected")

        public_key = Ed25519PublicKey.from_public_bytes(base64.b64decode(client.public_key))
        public_key.verify(base64.b64decode(signature), payload)
        session.add(ReplayNonce(install_id=install_id, nonce=nonce, path=request.url.path))
    return install_id
