#!/usr/bin/env python3
"""Read-only dev API verification. Auth credentials live only in memory."""
import http.cookiejar
import json
import urllib.error
import urllib.request

BASE = "https://dev-api.jachwi-sunbae.kr"
jar = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(path, method="GET", token=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, method=method, headers=headers)
    try:
        with client.open(req, timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def main():
    health_status, health = request("/actuator/health")
    info_status, info = request("/actuator/info")
    docs_status, docs = request("/v3/api-docs")
    assert health_status == info_status == docs_status == 200
    assert health["status"] == "UP"
    endpoint = "/api/maps/good-price-stores"
    assert docs["paths"][endpoint]["get"]["security"] == [{"bearerAuth": []}]
    query = endpoint + "?latitude=37.5665&longitude=126.978&radius=1000"
    assert request(query)[0] == 401
    status, login = request("/api/auth/login", "POST")
    assert status == 200
    status, refresh = request("/api/auth/refresh", "POST")
    assert status == 200 and login["data"]["member"] == refresh["data"]["member"]
    token = refresh["data"]["accessToken"]
    cookie = next(cookie for cookie in jar if cookie.name == "refresh_token")
    assert cookie.secure and cookie.has_nonstandard_attr("HttpOnly")
    checks = []
    for latitude, longitude, label in [(37.5665, 126.978, "서울 시청"), (37.4979, 127.0276, "강남역"), (37.5, 130, "해상 빈 결과")]:
        for radius in [500, 1000, 2000]:
            status, payload = request(f"{endpoint}?latitude={latitude}&longitude={longitude}&radius={radius}", token=token)
            assert status == 200, (label, radius, status)
            data = payload["data"]
            assert data["radius"] == radius and data["count"] == len(data["stores"])
            assert data["center"] == {"latitude": latitude, "longitude": longitude}
            for store in data["stores"]:
                assert isinstance(store["id"], int) and isinstance(store["name"], str)
                assert isinstance(store["latitude"], (int, float)) and isinstance(store["longitude"], (int, float))
                assert store["distanceMeters"] <= radius
                assert all(menu["price"] is None or isinstance(menu["price"], str) for menu in store["menus"])
            checks.append({"area": label, "radius": radius, "count": data["count"], "dataAsOf": data["dataAsOf"]})
    invalid_status, _ = request(endpoint + "?latitude=37.5665&longitude=126.978&radius=750", token=token)
    assert invalid_status == 400
    print(json.dumps({"health": health["status"], "deployedCommit": info["build"]["commit"],
                      "unauthenticatedStatus": 401, "refreshRestoredSameMember": True,
                      "secureHttpOnlyCookie": True, "invalidRadiusStatus": invalid_status,
                      "queries": checks}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
