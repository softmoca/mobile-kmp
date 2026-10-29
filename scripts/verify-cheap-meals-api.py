#!/usr/bin/env python3
"""Verify the deployed dev contract; never persist credentials or member IDs."""
import importlib.util
import json
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("dev_api", Path(__file__).with_name("verify-thrifty-api.py"))
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)


def main():
    status, info = dev.request("/actuator/info")
    assert status == 200
    status, docs = dev.request("/v3/api-docs")
    assert status == 200
    paths = [
        "/api/maps/good-price-stores?latitude=37.5665&longitude=126.978&radius=1000",
        "/api/promotions?brand=EMART24",
        "/api/promotions?brand=SEVEN_ELEVEN",
        "/api/delivery-discount-offers",
    ]
    for path in paths:
        assert docs["paths"][path.split("?")[0]]["get"]["security"] == [{"bearerAuth": []}]
        assert dev.request(path)[0] == 401
    status, login = dev.request("/api/auth/login", "POST")
    assert status == 200
    status, refresh = dev.request("/api/auth/refresh", "POST")
    assert status == 200 and refresh["data"]["member"] == login["data"]["member"]
    token = refresh["data"]["accessToken"]
    cookie = next(c for c in dev.jar if c.name == "refresh_token")
    assert cookie.secure and cookie.has_nonstandard_attr("HttpOnly")
    checks = []
    for path in paths:
        status, payload = dev.request(path, token=token)
        assert status == 200, (path, status)
        data = payload["data"]
        check = {"path": path, "status": status, "unauthenticatedStatus": 401}
        if "good-price-stores" in path:
            assert data["radius"] == 1000 and data["count"] == len(data["stores"])
            for store in data["stores"]:
                assert isinstance(store["businessType"], str) and store["distanceMeters"] <= 1000
                assert all(m["price"] is None or isinstance(m["price"], str) for m in store["menus"])
            check.update(count=data["count"], dataAsOf=data["dataAsOf"],
                         businessTypes=dict(Counter(s["businessType"] for s in data["stores"])))
        elif "promotions" in path:
            assert data["brand"] == path.split("=")[-1] and data["count"] == len(data["products"])
            assert data["collectedAt"] is None or isinstance(data["collectedAt"], str)
            assert data["sourceUrl"].startswith("https://")
            for product in data["products"]:
                assert type(product["price"]) is int and product["price"] >= 0
                assert isinstance(product["name"], str) and isinstance(product["imageUrl"], str)
                assert product["promotionType"] in {"ONE_PLUS_ONE", "TWO_PLUS_ONE", "THREE_PLUS_ONE", "SALE", "PICK_AND_MIX"}
                assert product["category"] in {"MEAL", "SNACK", "BEVERAGE", "HOUSEHOLD_GOODS", "UNKNOWN"}
            check.update(count=data["count"], collectedAt=data["collectedAt"], sourceUrl=data["sourceUrl"],
                         uncollected=data["count"] == 0 and data["collectedAt"] is None,
                         promotionTypes=dict(Counter(p["promotionType"] for p in data["products"])),
                         categories=dict(Counter(p["category"] for p in data["products"])))
        else:
            assert isinstance(data, list)
            for offer in data:
                assert offer["platform"] in {"BAEMIN", "COUPANG_EATS"}
                assert type(offer["discountAmount"]) is int and offer["discountAmount"] >= 0
                assert offer["minOrderAmount"] is None or type(offer["minOrderAmount"]) is int
                assert all(offer[k] is None or isinstance(offer[k], str) for k in ["startDate", "endDate", "conditions"])
                assert isinstance(offer["lastVerifiedAt"], str)
            check.update(count=len(data), platforms=dict(Counter(o["platform"] for o in data)))
        checks.append(check)
    print(json.dumps({"verifiedAt": datetime.now(ZoneInfo("Asia/Seoul")).isoformat(),
                      "apiOrigin": dev.BASE, "deployedCommit": info["build"]["commit"],
                      "refreshRestoredSameMember": True, "secureHttpOnlyCookie": True,
                      "queries": checks}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
