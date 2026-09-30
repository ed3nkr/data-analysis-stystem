"""collector 내부 API (FastAPI). backend 만 호출한다.

실행: uvicorn app.main:app --port 8000
"""
from __future__ import annotations

import asyncio
import logging
from datetime import date

import httpx
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from playwright.async_api import Error as PlaywrightError
from pydantic import BaseModel, Field

from . import naver
from .config import load_settings
from .errors import CollectorError, NaverUnreachable

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("collector")

settings = load_settings()
app = FastAPI(title="review collector (internal)", version="1.0")

# 동시에 한 건만 수집한다 (네이버에 부담을 주지 않기 위해). 나머지는 순서대로 기다린다.
_collect_lock = asyncio.Semaphore(1)


def ok(data) -> dict:
    return {"success": True, "data": data}


@app.exception_handler(CollectorError)
async def collector_error(_: Request, e: CollectorError):
    return JSONResponse(status_code=e.status,
                        content={"success": False, "error": {"code": e.code, "message": e.message}})


@app.exception_handler(RequestValidationError)
async def validation_error(_: Request, e: RequestValidationError):
    fields = ", ".join(".".join(str(p) for p in err["loc"][1:]) for err in e.errors())
    return JSONResponse(status_code=400,
                        content={"success": False, "error": {"code": "INVALID_INPUT", "message": f"잘못된 입력: {fields}"}})


@app.exception_handler(httpx.HTTPError)
async def network_error(_: Request, e: httpx.HTTPError):
    return await collector_error(_, NaverUnreachable(f"네이버에 연결하지 못했습니다: {e}"))


@app.exception_handler(PlaywrightError)
async def browser_error(_: Request, e: PlaywrightError):
    first_line = str(e).splitlines()[0] if str(e) else type(e).__name__
    return await collector_error(_, NaverUnreachable(f"페이지를 열지 못했습니다: {first_line}"))


@app.exception_handler(Exception)
async def unknown_error(_: Request, e: Exception):
    log.exception("처리되지 않은 오류")
    return JSONResponse(status_code=500,
                        content={"success": False, "error": {"code": "INTERNAL_ERROR", "message": str(e)[:300]}})


class ResolveRequest(BaseModel):
    placeUrl: str = Field(min_length=1, max_length=2000)


class CollectRequest(BaseModel):
    placeId: str = Field(pattern=r"^\d{1,20}$")
    since: date
    maxRequests: int = Field(default=200, ge=1, le=200)


@app.get("/internal/v1/health")
async def health():
    return ok({"status": "UP", "mode": "fixture" if settings.fixture_dir else "naver",
               "minIntervalSec": settings.min_interval_sec})


@app.post("/internal/v1/places/resolve")
async def resolve(req: ResolveRequest):
    info = await naver.resolve_place(settings, req.placeUrl)
    return ok({"placeId": info.place_id, "placeName": info.place_name, "address": info.address})


@app.post("/internal/v1/reviews/collect")
async def collect(req: CollectRequest):
    async with _collect_lock:
        result = await naver.collect_reviews(settings, req.placeId, req.since, req.maxRequests)
    return ok({"status": result.status, "stoppedReason": result.stopped_reason,
               "requestCount": result.request_count, "reviews": result.reviews})
