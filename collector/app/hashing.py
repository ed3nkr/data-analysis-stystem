"""리뷰 식별 해시. backend 의 ReviewHasher.java 와 반드시 같은 규칙을 쓴다.

author_hash = SHA-256(닉네임.strip() + salt)   — 닉네임이 없으면 None (원문은 반환·저장하지 않음)
dedup_key   = SHA-256(writtenAt(YYYY-MM-DD) + "|" + (author_hash 또는 "") + "|" + content.strip()[:50])
"""
import hashlib
from datetime import date


def _sha256(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def author_hash(nickname: str | None, salt: str) -> str | None:
    if nickname is None or not nickname.strip():
        return None
    return _sha256(nickname.strip() + salt)


def dedup_key(written_at: date, author_hash_value: str | None, content: str) -> str:
    body = (content or "").strip()
    return _sha256(f"{written_at.isoformat()}|{author_hash_value or ''}|{body[:50]}")
