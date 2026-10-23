#!/usr/bin/env python3
"""全模块 API 联调扫描（只读 GET 端点）。

用法：
  python3 scripts/e2e/api-sweep.py [--with-vars] [--out logs/api-sweep.json]

- 从 Controller 源码提取端点，只探活 GET（幂等、无副作用）。
- 经网关 8888 调用，带 admin JWT。
- 输出按模块汇总的 HTTP 状态分布与非 2xx 明细。
"""
import argparse
import json
import os
import subprocess
import sys
import time
from collections import Counter, defaultdict

GW = "http://127.0.0.1:8888"
IAM = "http://127.0.0.1:8081"
ROUTE_PREFIXES = [
    "/api/v1/extension",
    "/api/v1/iam",
    "/api/v1/apps",
    "/api/v1/masterdata",
    "/api/v1/orders",
    "/api/v1/payments",
    "/api/v1/channels",
    "/api/v1/channel-products",
    "/api/v1/channel-orders",
    "/api/v1/inventories",
    "/api/v1/shipments",
    "/api/v1/channel-broadcasts",
    "/api/v1/channel-buyers",
    "/api/v1/system",
    "/api/v1/console",
    "/api/v1/integration",
    "/api/v1/file",
    "/api/v1/notification",
    "/api/v1/generator",
    "/api/v1/metadata",
    "/api/v1/runtime",
]


def curl(args, timeout=15):
    cmd = ["curl", "-s", "--noproxy", "*", "-m", str(timeout)] + args
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
        return p.stdout
    except subprocess.TimeoutExpired:
        return ""


def login():
    out = curl(
        [
            "-X", "POST", IAM + "/api/v1/iam/login",
            "-H", "Content-Type: application/json",
            "-d", '{"username":"admin","password":"123456"}',
        ]
    )
    try:
        return json.loads(out)["data"]["token"]
    except Exception:
        sys.stderr.write("LOGIN FAILED: " + out[:300] + "\n")
        return None


def probe(token, path):
    full = GW + path
    cmd = [
        "-o", "/tmp/_sweep_body.txt", "-w", "%{http_code}",
        "-H", "Authorization: Bearer " + token, full,
    ]
    code = curl(cmd, timeout=20).strip()
    body = ""
    try:
        body = open("/tmp/_sweep_body.txt", encoding="utf-8", errors="ignore").read()[:400]
    except OSError:
        pass
    return code, body


def fill_vars(path):
    import re
    return re.sub(r"\{[^}]+\}", "1", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--with-vars", action="store_true", help="也探测带路径变量的端点")
    ap.add_argument("--out", default="logs/api-sweep.json")
    args = ap.parse_args()

    eps = json.load(open("/tmp/endpoints.json", encoding="utf-8"))
    token = login()
    if not token:
        return 2

    results = []
    for e in eps:
        if e["http"] != "GET":
            continue
        path = e["path"]
        if not any(path.startswith(p) for p in ROUTE_PREFIXES):
            continue
        if e["has_var"] and not args.with_vars:
            continue
        real = fill_vars(path) if e["has_var"] else path
        code, body = probe(token, real)
        results.append({**e, "probed": real, "status": code, "body": body})
        time.sleep(0.05)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    json.dump(results, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    by_mod = defaultdict(Counter)
    bad = []
    for r in results:
        s = r["status"]
        try:
            cls = str(int(s) // 100) + "xx"
        except ValueError:
            cls = "ERR"
        by_mod[r["module"]][cls] += 1
        if cls not in ("2xx",):
            bad.append(r)

    print("=== 模块 × 状态分布 ===")
    for m in sorted(by_mod):
        print(f"{m:45s} {dict(by_mod[m])}")
    print(f"\n=== 非 2xx 明细（{len(bad)}/{len(results)}）===")
    for r in bad:
        print(f"{r['status']}  {r['probed']}  [{r['controller']}]")
        if r["status"] == "500":
            print("      " + r["body"].replace("\n", " ")[:220])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
