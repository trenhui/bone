#!/usr/bin/env python3
"""Empirical JWT auth-chain probe for Bone backends.
Forges HS256 tokens (claims per JwtTokenService contract) with the dev-default
secret and a wrong-secret control, then probes live backends to determine
whether the running instances accept the shared secret.
"""
import base64
import hashlib
import hmac
import json
import time
import urllib.request
import urllib.error

DEV_SECRET = "dev-only-secret-key-minimum-32-bytes-long"
WRONG_SECRET = "wrong-secret-that-is-also-at-least-32-bytes-long!!"


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode("ascii")


def forge(secret: str) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": "admin",
        "userId": "1",
        "tenantId": "1",
        "scopes": ["*"],
        "iat": now,
        "exp": now + 7200,
    }
    seg1 = b64url(json.dumps(header, separators=(",", ":")).encode())
    seg2 = b64url(json.dumps(payload, separators=(",", ":")).encode())
    signing = f"{seg1}.{seg2}".encode()
    sig = hmac.new(secret.encode(), signing, hashlib.sha256).digest()
    return f"{seg1}.{seg2}.{b64url(sig)}"


def probe(name, url, token):
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("X-Tenant-Id", "1")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=8) as r:
            body = r.read(300).decode("utf-8", "replace")
            return r.status, body
    except urllib.error.HTTPError as e:
        body = e.read(300).decode("utf-8", "replace")
        return e.code, body
    except Exception as e:  # noqa
        return "ERR", str(e)[:200]


TARGETS = [
    ("masterdata", "http://localhost:18084/api/v1/masterdata/categories"),
    ("extension-studio", "http://localhost:18088/api/v1/extension/points"),
    ("file", "http://localhost:18089/api/v1/file/files"),
]

good = forge(DEV_SECRET)
bad = forge(WRONG_SECRET)

print("=== dev-default secret token ===")
for n, u in TARGETS:
    print(f"[{n}] {u}")
    print("   no-token     :", probe(n, u, ""))
    print("   wrong-secret :", probe(n, u, bad))
    print("   dev-secret   :", probe(n, u, good))
