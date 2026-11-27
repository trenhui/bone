#!/usr/bin/env python3
"""保司报送接收端桩（真实 HTTP 端点 + 报文落盘，零 mock 业务结论）。

与 tools/mock-ai-gateway.py（AI 网关桩、自报 mock=true）不同，本桩**代表保司系统的 HTTP 入口**：
真实监听端口、真实接收 POST、把每笔报文以 JSONL 追加落盘，供端到端测试做第二证据源对账
「Outbox SENT ↔ 保司确实收到且报文契约正确」——不是进程内假投递。

用法：
    python3 tools/mock-insurer-receiver.py --port 18082 --log /tmp/insurer-push-received.jsonl

    # 理赔服务指向本桩（重启 claim 8087 时注入）
    BONE_CLAIM_INSURER_PUSH_ENDPOINT=http://127.0.0.1:18082/api/v1/insurer/claims/push

端点：
    GET  /health                        -> 200 {"status":"ok","received":N}
    POST /api/v1/insurer/claims/push    -> 200 {"code":200,"receipt":"INS-...","received":N}
    GET  /received?claim_no=CL...       -> 200 命中的报文数组（联调查询用）
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import threading
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


class Receiver:
    """线程安全的内存态 + JSONL 追加落盘。"""

    def __init__(self, log_path: str) -> None:
        self.log_path = log_path
        self._lock = threading.Lock()
        self._records: list[dict] = []
        parent = os.path.dirname(os.path.abspath(log_path))
        if parent:
            os.makedirs(parent, exist_ok=True)

    def append(self, record: dict) -> int:
        with self._lock:
            self._records.append(record)
            with open(self.log_path, "a", encoding="utf-8") as fh:
                fh.write(json.dumps(record, ensure_ascii=False) + "\n")
                fh.flush()
            return len(self._records)

    def query(self, claim_no: str | None) -> list[dict]:
        with self._lock:
            if not claim_no:
                return list(self._records)
            return [
                r
                for r in self._records
                if claim_no == (r.get("payload") or {}).get("claimNo")
                or claim_no == r.get("claimNo")
            ]

    def count(self) -> int:
        with self._lock:
            return len(self._records)


class Handler(BaseHTTPRequestHandler):
    server_version = "insurer-receiver/1.0"

    @property
    def receiver(self) -> Receiver:
        return self.server.receiver  # type: ignore[attr-defined]

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler 约定
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") in ("/health", "/actuator/health"):
            self._reply(200, {"status": "ok", "received": self.receiver.count()})
            return
        if parsed.path.rstrip("/") == "/received":
            claim_no = (parse_qs(parsed.query).get("claim_no") or [None])[0]
            records = self.receiver.query(claim_no)
            self._reply(200, {"count": len(records), "records": records})
            return
        self._reply(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler 约定
        parsed = urlparse(self.path)
        if parsed.path.rstrip("/") != "/api/v1/insurer/claims/push":
            self._reply(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._reply(400, {"error": "invalid JSON body"})
            return
        if not isinstance(payload, dict) or "payload" not in payload:
            self._reply(422, {"error": "missing envelope.payload"})
            return
        body = payload.get("payload") or {}
        if not isinstance(body, dict) or not body.get("claimNo"):
            self._reply(422, {"error": "missing payload.claimNo"})
            return
        record = {
            "receivedAt": datetime.now(timezone.utc).isoformat(),
            "headers": {
                "X-Bone-Event-Id": self.headers.get("X-Bone-Event-Id", ""),
                "X-Bone-Event-Type": self.headers.get("X-Bone-Event-Type", ""),
                "X-Bone-Tenant-Id": self.headers.get("X-Bone-Tenant-Id", ""),
            },
            **payload,
        }
        n = self.receiver.append(record)
        receipt = "INS-" + str(n).zfill(8)
        self._reply(200, {"code": 200, "message": "OK", "receipt": receipt, "received": n})

    def _reply(self, status: int, body: dict) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt: str, *args) -> None:
        print("[insurer-receiver] " + fmt % args)


def main() -> int:
    parser = argparse.ArgumentParser(description="保司报送接收端桩（真实 HTTP + 报文落盘）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18082)
    parser.add_argument("--log", default="/tmp/insurer-push-received.jsonl")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.receiver = Receiver(args.log)  # type: ignore[attr-defined]
    print(
        f"insurer-receiver 监听 http://{args.host}:{args.port}"
        f"（POST /api/v1/insurer/claims/push，报文落盘 {args.log}）"
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
