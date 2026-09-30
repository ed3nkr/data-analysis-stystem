import os
from pathlib import Path

os.environ.setdefault("COLLECTOR_FIXTURE_DIR", str(Path(__file__).resolve().parents[1] / "fixtures_demo"))
os.environ.setdefault("COLLECTOR_MIN_INTERVAL_SEC", "0")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402

client = TestClient(app)


def test_health():
    r = client.get("/internal/v1/health")
    assert r.status_code == 200 and r.json()["data"]["status"] == "UP"


def test_resolve_ok():
    r = client.post("/internal/v1/places/resolve", json={"placeUrl": "https://m.place.naver.com/restaurant/1234567890/home"})
    assert r.status_code == 200
    assert r.json() == {"success": True, "data": {"placeId": "1234567890", "placeName": "가상 한식당 (fixture)",
                                                  "address": "서울특별시 가상구 예시로 12"}}


def test_resolve_invalid_url():
    r = client.post("/internal/v1/places/resolve", json={"placeUrl": "https://example.com/x"})
    assert r.status_code == 400 and r.json()["error"]["code"] == "INVALID_PLACE_URL"


def test_resolve_not_found():
    r = client.post("/internal/v1/places/resolve", json={"placeUrl": "https://map.naver.com/p/entry/place/999"})
    assert r.status_code == 404 and r.json()["error"]["code"] == "PLACE_NOT_FOUND"


def test_collect():
    r = client.post("/internal/v1/reviews/collect", json={"placeId": "1234567890", "since": "2026-09-01"})
    body = r.json()
    assert r.status_code == 200 and body["success"]
    assert body["data"]["stoppedReason"] == "REACHED_SINCE"
    assert all(x["writtenAt"] >= "2026-09-01" for x in body["data"]["reviews"])


def test_collect_validation():
    r = client.post("/internal/v1/reviews/collect", json={"placeId": "abc", "since": "2026-09-01", "maxRequests": 500})
    assert r.status_code == 400 and r.json()["error"]["code"] == "INVALID_INPUT"
