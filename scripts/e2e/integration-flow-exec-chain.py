#!/usr/bin/env python3
"""集成模块「建流程 → 激活 → 执行 → 看监控」闭环联调。

为什么需要它
------------
全端点扫描（api-sweep / real-id-sweep）显示 `/api/v1/integration/executions` **长期为空**：
UI 建出来的 25 个流程全是 DRAFT、零节点，从未被激活或执行过 ⇒
`MonitorController` 的详情 / 日志 / 重试 / 统计四个端点**从来没有真正跑过**，
属于「页面能渲染，但功能没被验证」的盲区。

本脚本按真实用户路径串起整条链，并把每一步的状态与响应打出来：
  建流程(带 START/LOG/END 节点) → 回读节点 id → 补连线 → 激活 → 执行
  → 执行列表 / 详情 / 日志 / 重试 / 统计。

判据：任一步非 2xx 即判失败并打印响应体（不静默吞错）。

用法：
  python3 scripts/e2e/integration-flow-exec-chain.py
"""
import json
import subprocess
import time

from e2e_lib import http

GW = "http://127.0.0.1:8888"
STAMP = str(int(time.time()))[-6:]


def curl(args, timeout=30):
    cmd = ["curl", "-s", "--noproxy", "*", "-m", str(timeout)] + args
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
        return p.stdout
    except subprocess.TimeoutExpired:
        return ""


def req(token, method, path, body=None, expect=("2",), label=""):
    args = ["-o", "/tmp/_chain_body.txt", "-w", "%{http_code}"]
    if token:
        args += ["-H", "Authorization: Bearer " + token]
    if body is not None:
        args += ["-H", "Content-Type: application/json", "-X", method, "-d", json.dumps(body)]
    else:
        args += ["-X", method]
    args.append(GW + path)
    code = curl(args).strip()
    try:
        raw = open("/tmp/_chain_body.txt", encoding="utf-8", errors="ignore").read()
    except OSError:
        raw = ""
    ok = code.startswith(expect)
    print(f"  [{'OK ' if ok else 'BAD'}] {code} {method} {path}  {label}")
    if not ok:
        print("        " + raw[:400].replace("\n", " "))
    try:
        return code, json.loads(raw)
    except Exception:
        return code, {"_raw": raw}


def main():
    # 登录样板走公共层（失败直接 SystemExit，退出码与原先未捕获异常时一致）；
    # 后续请求仍走 curl --noproxy：状态码字符串 + 原始 body 是本链路的判据口径
    token = http.login()
    failures = []

    print("== 1. 建流程（带 START / LOG / END 三个节点）==")
    payload = {
        "name": f"E2ECHAIN{STAMP}",
        "description": f"E2E 执行链自测 {STAMP}",
        "nodes": [
            {"name": "开始", "type": "START", "config": {}, "positionX": 100, "positionY": 100},
            {"name": "打日志", "type": "LOG", "config": {"message": "hello"}, "positionX": 300, "positionY": 100},
            {"name": "结束", "type": "END", "config": {}, "positionX": 500, "positionY": 100},
        ],
        "connections": [],
    }
    code, res = req(token, "POST", "/api/v1/integration/flows", payload, label="create")
    if not code.startswith("2"):
        return 2
    flow_id = res["data"]

    print("== 2. 回读流程详情，取真实节点 id ==")
    code, res = req(token, "GET", f"/api/v1/integration/flows/{flow_id}", label="detail")
    nodes = (res.get("data") or {}).get("nodes") or []
    if len(nodes) < 3:
        print(f"  !! 节点数不足（{len(nodes)}），无法连线")
        failures.append("nodes<3")
        return dump(flow_id, failures)
    node_ids = [n["id"] for n in nodes]
    print(f"     节点 id: {node_ids}")

    print("== 3. 补连线（START→LOG→END）==")
    upd = {
        "name": f"E2ECHAIN{STAMP}",
        "description": f"E2E 执行链自测 {STAMP}",
        "nodes": [
            {"name": "开始", "type": "START", "config": {}, "positionX": 100, "positionY": 100},
            {"name": "打日志", "type": "LOG", "config": {"message": "hello"}, "positionX": 300, "positionY": 100},
            {"name": "结束", "type": "END", "config": {}, "positionX": 500, "positionY": 100},
        ],
        "connections": [
            {"sourceNodeId": int(node_ids[0]), "targetNodeId": int(node_ids[1]), "condition": None},
            {"sourceNodeId": int(node_ids[1]), "targetNodeId": int(node_ids[2]), "condition": None},
        ],
    }
    code, _ = req(token, "PUT", f"/api/v1/integration/flows/{flow_id}", upd, label="connect")
    if not code.startswith("2"):
        failures.append("connect")

    print("== 4. 激活 ==")
    code, _ = req(token, "POST", f"/api/v1/integration/flows/{flow_id}/activate", label="activate")
    if not code.startswith("2"):
        failures.append("activate")

    print("== 5. 执行（test 端点，带 inputData）==")
    code, res = req(token, "POST", f"/api/v1/integration/flows/{flow_id}/test",
                    {"inputData": {"hello": "world"}}, label="execute")
    if not code.startswith("2"):
        failures.append("execute")
        return dump(flow_id, failures)
    exec_id = res["data"]
    print(f"     executionId={exec_id}")

    print("== 6. 监控闭环 ==")
    for label, method, path in [
        ("执行列表", "GET", "/api/v1/integration/executions?page=1&size=5"),
        ("执行详情", "GET", f"/api/v1/integration/executions/{exec_id}"),
        ("执行日志行", "GET", f"/api/v1/integration/executions/{exec_id}/logs"),
        ("流程统计", "GET", f"/api/v1/integration/statistics?flowId={flow_id}"),
        ("流程版本", "GET", f"/api/v1/integration/flows/{flow_id}/versions"),
        ("流程列表", "GET", "/api/v1/integration/flows?page=1&size=3"),
    ]:
        code, res = req(token, method, path, label=label)
        if not code.startswith("2"):
            failures.append(path)

    print("== 7. 失败重试（retry）==")
    code, res = req(token, "POST", f"/api/v1/integration/executions/{exec_id}/retry", label="retry")
    if not code.startswith("2"):
        failures.append("retry")

    return dump(flow_id, failures)


def dump(flow_id, failures):
    print("\n=== 结论 ===")
    print(f"flowId={flow_id}")
    if failures:
        print(f"失败 {len(failures)} 项: {failures}")
        return 1
    print("整条执行链 2xx 闭环（建流程→连线→激活→执行→监控→重试）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
