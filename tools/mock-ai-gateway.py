#!/usr/bin/env python3
"""本地 AI 网关桩（OpenAI 兼容），用于**联调链路**，不用于任何结论性场景。

为什么需要它：部署环境暂时没有可用的模型端点时，`bone.ai.gateway.base-url` 为空会让
bone-ai-gateway 退回进程内 Mock；那个 Mock 不监听端口，无法验证「HTTP 出站 → 契约解析 →
归因码」这一段是否真的通。本脚本补的就是这一段：一个真实的 HTTP 端点，返回符合契约的响应体。

**它不会让影像识别“假通过”**：响应体显式带 `"mock": true`，
`ImageDocumentParsePortAdapter` 见到该字段会直接落 `IMAGE_MOCK_REJECTED` 并转人工。
这正是我们要验证的降级诚实语义——桩存在 ≠ 结论可信。

用法：
    python3 tools/mock-ai-gateway.py --port 18080
    # 另一个终端
    BONE_AI_GATEWAY_BASE_URL=http://127.0.0.1:18080/v1 \
    BONE_AI_GATEWAY_API_KEY=mock-key \
    BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED=true \
    java -jar bone-apps/synap-claim/target/synap-claim.jar

    然后提交一张图像件，预期看到：
      · 适配器被装配（不再是 IMAGE_ADAPTER_MISSING）
      · 解析结果落 FAILED，归因码 IMAGE_MOCK_REJECTED
      · 案件转人工补录，**没有**任何金额被自动核准

验证真实连通（文本层）：
    curl -sS http://127.0.0.1:18080/v1/chat/completions \
      -H 'Content-Type: application/json' \
      -d '{"model":"mock","messages":[{"role":"user","content":"hi"}]}'
"""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 与 ImageDocumentParsePortAdapter 的契约保持同构：fields 数组 + 每项 code/value/confidence/evidenceRef。
_IMAGE_FIELDS = [
    {"code": "invoice_no", "value": "00000000", "confidence": 0.99, "evidenceRef": "image@0:0"},
    {"code": "invoice_date", "value": "2026-01-01", "confidence": 0.99, "evidenceRef": "image@0:0"},
    {"code": "total_amount", "value": "1.00", "confidence": 0.99, "evidenceRef": "image@0:0"},
]


class Handler(BaseHTTPRequestHandler):
    server_version = "mock-ai-gateway/1.0"

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler 约定
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._reply(400, {"error": {"message": "invalid JSON body"}})
            return

        messages = payload.get("messages") or []
        has_image = any(
            isinstance(part, dict) and part.get("type") == "image_url"
            for message in messages
            for part in (message.get("content") or [])
            if isinstance(part, dict)
        )

        content = json.dumps(
            {
                # 显式自报 mock：调用方据此拒绝，绝不能被当成真实模型结论。
                "mock": True,
                "available": True,
                "reason": "MOCK_GATEWAY",
                "fields": _IMAGE_FIELDS if has_image else [],
            },
            ensure_ascii=False,
        )
        self._reply(
            200,
            {
                "id": "mock-cmpl-1",
                "object": "chat.completion",
                "model": payload.get("model") or "mock",
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": content},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            },
        )

    def do_GET(self) -> None:  # noqa: N802
        if self.path.rstrip("/") in ("/health", "/v1/health"):
            self._reply(200, {"status": "ok", "mock": True})
        else:
            self._reply(404, {"error": {"message": "not found"}})

    def _reply(self, status: int, body: dict) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt: str, *args) -> None:
        print("[mock-ai-gateway] " + fmt % args)


def main() -> int:
    parser = argparse.ArgumentParser(description="本地 OpenAI 兼容网关桩（联调用，会自报 mock）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"mock-ai-gateway 监听 http://{args.host}:{args.port}/v1（响应自带 mock=true，会被显式拒绝）")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
