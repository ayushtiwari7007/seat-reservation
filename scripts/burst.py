#!/usr/bin/env python3
"""Exercise hot-seat, idempotency, per-user-limit, and state reconciliation."""

import concurrent.futures
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter


def request(base, path, method="GET", payload=None, token=None, headers=None):
    data = None if payload is None else json.dumps(payload).encode()
    request_headers = {"Accept": "application/json"}
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = "Bearer " + token
    if headers:
        request_headers.update(headers)
    req = urllib.request.Request(base + path, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=float(os.getenv("HTTP_TIMEOUT", "90"))) as response:
            body = response.read()
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        body = error.read()
        try:
            parsed = json.loads(body) if body else {}
        except json.JSONDecodeError:
            parsed = {"errorMessage": body.decode(errors="replace")}
        return error.code, parsed
    except Exception as error:  # Record transport failures separately from HTTP 5xx.
        return 0, {"errorMessage": str(error)}


def token_for(base, user, role="USER"):
    status, body = request(base, "/auth/token", "POST", {"user_id": user, "role": role})
    if status != 200 or "token" not in body:
        raise RuntimeError(f"token request failed ({status}): {body}")
    return body["token"]


def reserve(base, show_id, token, seat, key):
    return request(base, f"/shows/{show_id}/reserve", "POST", {"seats": [seat]}, token,
                   {"Idempotency-Key": key, "X-Request-Id": str(uuid.uuid4())})


def classify(result):
    status, body = result
    if status == 201:
        return "confirmed"
    if status == 409:
        message = str(body.get("errorMessage", "")).lower()
        if "per-user" in message or "per user" in message:
            return "per_user_limit"
        if "idempotency key" in message:
            return "idempotency_conflict"
        if "unavailable" in message:
            return "seat_taken"
        return "declined_other"
    if status >= 500:
        return "5xx"
    if status == 0:
        return "transport_error"
    return f"unexpected_{status}"


def main():
    if len(sys.argv) != 2:
        print("Usage: ./burst.sh <BASE_URL>", file=sys.stderr)
        return 2
    base = sys.argv[1].rstrip("/")
    users = int(os.getenv("BURST_USERS", "500"))
    workers = max(2, int(os.getenv("BURST_WORKERS", str(min(users, 32)))))
    per_user_limit = 4
    if users < 2 or workers < 2:
        raise ValueError("BURST_USERS and BURST_WORKERS must both be at least 2")

    suffix = uuid.uuid4().hex[:12]
    admin_token = token_for(base, "burst-admin-" + suffix, "ADMIN")
    labels = ["HOT-1", "RETRY-1"] + [f"LIMIT-{index}" for index in range(1, 11)]
    status, created = request(base, "/shows", "POST", {
        "name": "burst-" + suffix,
        "seats": labels,
        "price_paise": 100,
        "per_user_limit": per_user_limit,
    }, admin_token)
    if status != 201:
        raise RuntimeError(f"show creation failed ({status}): {created}")
    show_id = created["id"]

    print(f"Show: {show_id}; hot-seat requests: {users}; worker concurrency: {workers}")
    print("Preparing authenticated users...")
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(workers, 128)) as pool:
        tokens = list(pool.map(lambda index: token_for(base, f"burst-{suffix}-user-{index}"), range(users)))
    print(f"Prepared {len(tokens)} tokens in {time.monotonic() - started:.1f}s")

    # Release the first worker wave together to create a genuine hot-seat race.
    barrier = threading.Barrier(min(users, workers))
    def hot_attempt(index):
        if index < barrier.parties:
            barrier.wait(timeout=60)
        return reserve(base, show_id, tokens[index], "HOT-1", f"hot-{suffix}-{index}")

    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        hot_results = list(pool.map(hot_attempt, range(users)))
    hot_counts = Counter(classify(result) for result in hot_results)
    print(f"Hot-seat results ({time.monotonic() - started:.1f}s): {dict(hot_counts)}")

    # A successful key retry must return the same reservation; a changed body must conflict.
    replay_token = token_for(base, "burst-" + suffix + "-replay")
    replay_key = "retry-" + suffix
    first = reserve(base, show_id, replay_token, "RETRY-1", replay_key)
    replay = reserve(base, show_id, replay_token, "RETRY-1", replay_key)
    mismatch = reserve(base, show_id, replay_token, "LIMIT-1", replay_key)
    replay_ok = first[0] == replay[0] == 201 and first[1].get("reservation_id") == replay[1].get("reservation_id")
    mismatch_ok = mismatch[0] == 409
    print(f"Idempotency: first={first[0]} replay={replay[0]} same_reservation={replay_ok} changed_body={mismatch[0]}")

    # One identity sends ten distinct requests concurrently; the show limit remains four.
    limit_token = token_for(base, "burst-" + suffix + "-limit")
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        limit_results = list(pool.map(
            lambda index: reserve(base, show_id, limit_token, f"LIMIT-{index}", f"limit-{suffix}-{index}"),
            range(1, 11)))
    limit_counts = Counter(classify(result) for result in limit_results)
    print(f"Per-user-limit results: {dict(limit_counts)}")

    state_status, state = request(base, f"/shows/{show_id}", token=admin_token)
    total = state.get("total_seats", -1)
    available = state.get("available_seats", -1)
    held = state.get("held_seats", -1)
    confirmed = state.get("confirmed_seats", -1)
    invariant_ok = state_status == 200 and available + held + confirmed == total
    all_results = hot_results + [first, replay, mismatch] + limit_results
    distribution = Counter(classify(result) for result in all_results)
    print(f"All requests: {dict(distribution)}")
    print(f"Final state: status={state_status} available={available} held={held} confirmed={confirmed} total={total} invariant_ok={invariant_ok}")

    hot_ok = hot_counts["confirmed"] == 1 and hot_counts["seat_taken"] == users - 1
    limit_ok = limit_counts["confirmed"] == per_user_limit and limit_counts["per_user_limit"] == 10 - per_user_limit
    five_xx = distribution["5xx"]
    if not (hot_ok and replay_ok and mismatch_ok and limit_ok and invariant_ok and five_xx == 0):
        print("BURST CHECK FAILED", file=sys.stderr)
        return 1
    print("BURST CHECK PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"BURST SETUP/EXECUTION FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
