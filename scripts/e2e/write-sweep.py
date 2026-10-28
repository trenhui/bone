#!/usr/bin/env python3
"""写端点空体扫描：找出「客户端输入错误被报成服务端故障」的缺陷。

原理：写端点（POST/PUT/PATCH）用空 JSON 体 {} 调用。正确实现应返回
400/415（客户端错误），因为必填字段为空是调用方的问题。
若返回 5xx，说明该模块 GlobalExceptionHandler 缺分支，异常被 catch-all
兜底成了 500 —— 这是真实的正确性缺陷（污染监控告警、误导前端错误处理）。

用法：
  python3 scripts/e2e/write-sweep.py [--out logs/write-sweep.json]
"""
import argparse
import json
import os
import subprocess
import sys
import time
from collections import Counter, defaultdict

from e2e_lib import http

GW = "http://127.0.0.1:8888"
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
# 501 = 设计如此（能力未实现），不算缺陷
BENIGN_5XX = {501}


def curl(args, timeout=20):
    cmd = ["curl", "-s", "--noproxy", "*", "-m", str(timeout)] + args
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5).stdout
    except subprocess.TimeoutExpired:
        return ""


def login():
    # 登录样板走公共层（失败需返回 None 让 main 判 2，故捕获其 SystemExit）；
    # 后续探测仍走 curl --noproxy：状态码字符串 + 原始 body 是本扫描的测量契约
    try:
        return http.login()
    except SystemExit as e:
        sys.stderr.write("LOGIN FAILED: " + str(e) + "\n")
        return None


def fill_vars(path):
    """把 {id} 之类路径变量替换成占位值。"""
    import re

    return re.sub(r"\{[^}]+\}", "1", path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="logs/write-sweep.json")
    args = ap.parse_args()

    eps = json.load(open("/tmp/endpoints.json", encoding="utf-8"))
    token = login()
    if not token:
        return 2

    targets = [
        e
        for e in eps
        if e["http"] in ("POST", "PUT", "PATCH")
        and any(e["path"].startswith(p) for p in ROUTE_PREFIXES)
    ]

    results = []
    for e in targets:
        real = fill_vars(e["path"])
        body_file = "/tmp/_write_sweep_body.txt"
        code = curl(
            [
                "-o", body_file, "-w", "%{http_code}",
                "-X", e["http"],
                "-H", "Authorization: Bearer " + token,
                "-H", "Content-Type: application/json",
                "-d", "{}",
                GW + real,
            ]
        ).strip()
        try:
            body = open(body_file, encoding="utf-8", errors="ignore").read()[:400]
        except OSError:
            body = ""
        results.append({**e, "probed": real, "status": code, "body": body})
        time.sleep(0.03)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    json.dump(results, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    by_mod = defaultdict(Counter)
    for r in results:
        s = r["status"]
        cls = str(int(s) // 100) + "xx" if s.isdigit() else "ERR"
        by_mod[r["module"]][cls] += 1

    print("=== 写端点 × 模块 × 状态分布 ===")
    for m in sorted(by_mod):
        print(f"{m:45s} {dict(by_mod[m])}")

    bad = [
        r
        for r in results
        if r["status"].isdigit()
        and int(r["status"]) >= 500
        and int(r["status"]) not in BENIGN_5XX
    ]
    print(f"\n=== 真实缺陷：5xx 写端点（{len(bad)}/{len(results)}）===")
    for r in bad:
        print(f"{r['status']}  {r['http']:6s} {r['probed']}  [{r['controller']}]")

    benign = [
        r for r in results
        if r["status"].isdigit() and int(r["status"]) in BENIGN_5XX
    ]
    if benign:
        print(f"\n=== 501 设计如此（{len(benign)}，非缺陷）===")
        for r in benign:
            print(f"  501  {r['http']:6s} {r['probed']}")

    over_permissive = [
        r for r in results if r["status"].isdigit() and int(r["status"]) // 100 == 2
    ]
    print(f"\n=== 空体仍 2xx 的写端点（{len(over_permissive)}，缺入参校验）===")
    for r in over_permissive:
        print(f"  {r['status']} {r['http']:6s} {r['probed']}  [{r['controller']}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
