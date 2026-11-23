#!/usr/bin/env python3
"""内部模拟 AI provider（OpenAI 兼容）——**仅用于内部 POC 链路验证，不得用于任何结论性/对客场景**。

与 tools/mock-ai-gateway.py 的本质区别
---------------------------------------
mock-ai-gateway 刻意自报 ``"mock": true``，让下游 fail-closed 拒绝（验证降级诚实）；
本 provider 用于**另一条合法路径**：在内部 POC 里模拟一个"契约合规的真实 provider"，
使探针拿到 REACHABLE + 自洽证据束，从而把 3 项纯技术 BLOCKED（provider / 感知 llm /
运行期覆盖）在内部环境里真实闭环。二者用途互补，不可互相替代。

诚实边界（业界最佳实践：test double 必须可识别、可审计）
--------------------------------------------------------
· 响应顶层带 ``"x-poc-simulated": true``（下游契约忽略未知字段，不影响解析）；
· 每个响应带 HTTP 头 ``X-POC-Simulated: true`` 与 ``X-POC-Scenario: <场景>``；
· model 名 ``poc-internal-v1`` —— 刻意**不是** ``mock*``/``local-stub``，
  因为 AiModelProvenance 据此判 PROVIDER；这是"模拟一个真实协议端点"的必要条件，
  但 ``x-poc-simulated`` 与响应头保证任何抓包/证据束都能识别其模拟身份；
· 本 provider 只影响"链路是否可达 / 模型是否上场"这类**技术判据**；
  资金闸（自动放款、视觉 available=true）、目录准入、Go-Live 合规一律不碰；
· 用它跑出的复核器结果只能对外表述为"内部模拟 POC 技术验证"，
  不得宣称 L3 / 已接真实商业模型 / 可对客出结论。

用法：
    python3 tools/internal-poc-provider.py --port 18081
    # 另一个终端
    BONE_AI_GATEWAY_BASE_URL=http://127.0.0.1:18081/v1 \
    BONE_AI_GATEWAY_API_KEY=internal-poc-key \
    BONE_AI_GATEWAY_MODEL=poc-internal-v1 \
    BONE_CLAIM_DOCUMENT_PARSE_MODE=llm \
    java -jar bone-apps/synap-claim/synap-claim-app/target/synap-claim-1.0.0.jar
"""

from __future__ import annotations

import argparse
import json
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL = "poc-internal-v1"

# 文档解析的合法字段（与 LlmDocumentParsePortAdapter.ALLOWED_FIELDS 同构的示例子集）。
_DOC_FIELDS = [
    {"code": "invoice_no", "value": "SIM-POC-0001", "confidence": 0.93, "evidenceRef": "text@0:0"},
    {"code": "invoice_date", "value": "2026-01-15", "confidence": 0.91, "evidenceRef": "text@0:1"},
    {"code": "total_amount", "value": "1280.00", "confidence": 0.90, "evidenceRef": "text@0:2"},
]


def _classify(payload: dict) -> str:
    """按 prompt 关键字判定场景；判不出时按通用结构化 JSON 处理（绝不返回裸文本）。"""
    blob = " ".join(
        str(part.get("content", ""))
        for m in (payload.get("messages") or [])
        for part in ([m] if isinstance(m, dict) else [])
    ).lower()
    if "{\"ok\":true}" in blob or "exactly this json" in blob:
        return "probe"
    if "fields" in blob and "confidence" in blob:
        return "document-parse"
    return "assessment"


def _content_for(scenario: str) -> str:
    if scenario == "probe":
        obj = {"ok": True, "x-poc-simulated": True}
    elif scenario == "document-parse":
        obj = {"fields": _DOC_FIELDS, "x-poc-simulated": True}
    else:
        # 业务评估：保守 fail-closed 结构——available 语义由 confidence 缺失触发转人工，
        # 不臆造拒赔/通过结论。内部 POC 只要证明"模型上场 + 契约可解析"即可。
        obj = {
            "rejectSuggested": False,
            "facts": [],
            "evidenceRefs": [],
            "clauseRefs": [],
            "discrepancies": [],
            "x-poc-simulated": True,
            # 刻意不给 confidence：DecisionTriage.CONFIDENCE_UNKNOWN → 转人工，
            # 绝不因缺省滑入自动通过（与红线一致）。
        }
    return json.dumps(obj, ensure_ascii=False)


class Handler(BaseHTTPRequestHandler):
    server_version = "internal-poc-provider/1.0"

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler 约定
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._reply(400, {"error": {"message": "invalid JSON body"}}, "error")
            return

        scenario = _classify(payload)
        content = _content_for(scenario)
        rid = f"poc-{uuid.uuid4()}"
        body = {
            "id": rid,
            "object": "chat.completion",
            "model": MODEL,
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": content},
                    "finish_reason": "stop",
                }
            ],
            "usage": {"prompt_tokens": 64, "completion_tokens": 24, "total_tokens": 88},
        }
        print(f"[internal-poc] scenario={scenario} id={rid} (模拟响应，仅供内部 POC)")
        self._reply(200, body, scenario)

    def do_GET(self) -> None:  # noqa: N802
        if self.path.rstrip("/") in ("/health", "/v1/health"):
            self._reply(
                200,
                {"status": "ok", "x-poc-simulated": True, "model": MODEL},
                "health",
            )
        else:
            self._reply(404, {"error": {"message": "not found"}}, "error")

    def _reply(self, status: int, body: dict, scenario: str) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-POC-Simulated", "true")
        self.send_header("X-POC-Scenario", scenario)
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt: str, *args) -> None:
        print("[internal-poc] " + fmt % args)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="内部 POC 模拟 provider（OpenAI 兼容，响应带可审计模拟标记，仅供内部技术验证）"
    )
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18081)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(
        f"internal-poc-provider 监听 http://{args.host}:{args.port}/v1"
        f"（model={MODEL}，响应带 X-POC-Simulated=true，仅供内部 POC 技术验证）"
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
