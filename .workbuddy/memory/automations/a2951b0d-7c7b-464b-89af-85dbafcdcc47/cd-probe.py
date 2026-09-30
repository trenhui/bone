#!/usr/bin/env python3
"""Bone C/D integration probe (auth chain already unlocked via shared dev secret).
Verifies masterdata read + self-cleaning create/delete (full CRUD D flow),
and authenticated smoke on file + extension-studio.
"""
import base64
import hashlib
import hmac
import json
import time
import urllib.request
import urllib.error
import urllib.parse

SECRET = "dev-only-secret-key-minimum-32-bytes-long"


def b64url(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def forge(scopes):
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": "admin",
        "userId": "1",
        "tenantId": "1",
        "scopes": scopes,
        "iat": now,
        "exp": now + 7200,
    }
    s1 = b64url(json.dumps(header, separators=(",", ":")).encode())
    s2 = b64url(json.dumps(payload, separators=(",", ":")).encode())
    sig = hmac.new(SECRET.encode(), f"{s1}.{s2}".encode(), hashlib.sha256).digest()
    return f"{s1}.{s2}.{b64url(sig)}"


READ = forge(["*", "masterdata:categories:read"])
WRITE = forge(["*", "masterdata:categories:write"])


def call(method, url, token, body=None, hdr=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("X-Tenant-Id", "1")
    req.add_header("Accept", "application/json")
    if hdr:
        for k, v in hdr.items():
            req.add_header(k, v)
    if body is not None:
        req.add_header("Content-Type", "application/json")
        req.data = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read(400).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read(400).decode("utf-8", "replace")
    except Exception as e:  # noqa
        return "ERR", str(e)[:200]


print("########## bone-masterdata (18084) C/D ##########")
# C: read tree
st, b = call("GET", "http://localhost:18084/api/v1/masterdata/categories?masterDataEntityId=1", READ)
print(f"[C] GET /categories?masterDataEntityId=1 -> {st} | {b[:160]}")

# D: create (self-cleaning)
code = f"cd-it-{int(time.time())}"
st, b = call("POST", "http://localhost:18084/api/v1/masterdata/categories",
             WRITE, {"masterDataEntityId": 1, "code": code, "name": "IT Probe Category",
                     "description": "automation C/D probe"})
print(f"[D] POST /categories -> {st} | {b[:200]}")
new_id = None
if st == 200:
    try:
        new_id = json.loads(b).get("data")
    except Exception:
        pass
    print(f"    created id = {new_id}")

# D: delete (cleanup)
if new_id:
    st, b = call("DELETE", f"http://localhost:18084/api/v1/masterdata/categories/{new_id}", WRITE)
    print(f"[D] DELETE /categories/{new_id} -> {st} | {b[:120]}")
    # verify gone
    st2, _ = call("GET", f"http://localhost:18084/api/v1/masterdata/categories/record/{new_id}", READ)
    print(f"    post-delete read -> {st2}")

print("\n########## bone-file (18089) smoke ##########")
st, b = call("GET", "http://localhost:18089/api/v1/file/files/test", READ)
print(f"[C] GET /file/files/test -> {st} | {b[:160]}")
# unauth control
st, b = call("GET", "http://localhost:18089/api/v1/file/files/test", "")
print(f"[ctrl] no-token -> {st}")

print("\n########## bone-extension-studio (18088) auth verify ##########")
st, b = call("GET", "http://localhost:18088/api/v1/extension/plugins", READ)
print(f"[C] GET /extension/plugins (token) -> {st} | {b[:160]}")
st, b = call("GET", "http://localhost:18088/api/v1/extension/plugins", "")
print(f"[ctrl] GET /extension/plugins (no token) -> {st}")
