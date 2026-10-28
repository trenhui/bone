#!/usr/bin/env python3
"""带**真实 ID** 的全模块 GET 端点联调扫描。

与 api-sweep.py 的分工
----------------------
api-sweep.py 用 `1` 填充所有路径变量（fill_vars），命中不存在的资源 ⇒ 大量 **404 是占位符造成的**，
并非产品缺陷，但代价是：这些「详情 / 子资源」端点**从来没有被真正执行过**，
里面潜藏的 NPE、类型转换错误、SQL 错误一概扫不出来。

本脚本把变量换成**真实存在的资源 ID**：
  1. 从端点路径截出「集合前缀」（第一个 `{var}` 之前的部分），GET 之；
  2. 从响应 `data` 里递归挑出一个真实 id（list / records / content / items / rows / data…）；
  3. 回填后再看还有没有下一个变量，若有则把已回填的路径当新前缀重复 1-2；
  4. 全部回填完才真正探测，并把「解析到真实 ID」与「未能解析」分开统计。

判据：只把 **5xx** 当缺陷（4xx 需结合端点语义人工判定，这里只列出不判死）。

用法：
  python3 scripts/e2e/real-id-sweep.py [--out logs/real-id-sweep.json]
"""
import argparse
import json
import os
import re
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

VAR_RE = re.compile(r"\{[^}]+\}")
# 可能的列表容器键
LIST_KEYS = ("list", "records", "content", "items", "rows", "result", "data", "elements", "nodes")
# 可能的 id 键（按优先级）
ID_KEYS = ("id", "recordId", "entityId", "flowId", "code", "entityCode", "name", "typeCode", "dictType")


def curl(args, timeout=20):
    cmd = ["curl", "-s", "--noproxy", "*", "-m", str(timeout)] + args
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5)
        return p.stdout
    except subprocess.TimeoutExpired:
        return ""


def login():
    # 登录样板走公共层（失败需返回 None 让 main 判 2，故捕获其 SystemExit）；
    # 后续解析/探测仍走 curl --noproxy：状态码字符串 + 原始 body 是本扫描的测量契约
    try:
        return http.login()
    except SystemExit as e:
        sys.stderr.write("LOGIN FAILED: " + str(e) + "\n")
        return None


def get_json(token, path, timeout=20):
    """GET path，返回 (http_code, parsed_json_or_None)。"""
    cmd = [
        "-o", "/tmp/_rid_body.txt", "-w", "%{http_code}",
        "-H", "Authorization: Bearer " + token, GW + path,
    ]
    code = curl(cmd, timeout=timeout).strip()
    try:
        raw = open("/tmp/_rid_body.txt", encoding="utf-8", errors="ignore").read()
    except OSError:
        raw = ""
    try:
        return code, json.loads(raw)
    except Exception:
        return code, None


def pick_id(payload, prefer_key=None):
    """从 ApiResponse 的 data 里递归挑一个真实 id；挑不到返回 None。"""
    if not isinstance(payload, dict):
        return None
    data = payload.get("data", payload)

    def from_obj(obj):
        if not isinstance(obj, dict):
            return None
        if prefer_key and obj.get(prefer_key) not in (None, ""):
            return obj[prefer_key]
        for k in ID_KEYS:
            v = obj.get(k)
            if isinstance(v, (int,)) and v > 0:
                return v
            if isinstance(v, str) and v.strip() and len(v) < 128 and k in ("code", "entityCode", "name", "typeCode", "dictType"):
                return v
        # 再深一层：容器键
        for k in LIST_KEYS:
            sub = obj.get(k)
            if isinstance(sub, list) and sub:
                r = from_obj(sub[0]) if isinstance(sub[0], dict) else None
                if r is not None:
                    return r
            elif isinstance(sub, dict):
                r = from_obj(sub)
                if r is not None:
                    return r
        return None

    if isinstance(data, list):
        for it in data:
            r = from_obj(it)
            if r is not None:
                return r
        return None
    return from_obj(data)


def resolve(token, path, cache):
    """把 path 里的路径变量逐个换成真实 ID。

    返回 (resolved_path, n_filled, n_total, note)
    """
    n_total = len(VAR_RE.findall(path))
    cur = path
    filled = 0
    guard = 0
    while True:
        m = VAR_RE.search(cur)
        if not m:
            break
        guard += 1
        if guard > 6:
            return cur, filled, n_total, "too-many-vars"
        prefix = cur[: m.start()].rstrip("/")
        name = m.group(0)[1:-1]
        if prefix in cache:
            val = cache[prefix]
        else:
            code, payload = get_json(token, prefix)
            if code.startswith("2") and payload is not None:
                val = pick_id(payload, name)
            else:
                val = None
            if val is None:
                # 有些列表端点要求分页参数
                code2, payload2 = get_json(token, prefix + "?page=1&size=20")
                val = pick_id(payload2, name) if (code2.startswith("2") and payload2 is not None) else None
            cache[prefix] = val
        if val is None:
            return cur, filled, n_total, "unresolved@" + name
        cur = cur[: m.start()] + str(val) + cur[m.end():]
        filled += 1
    return cur, filled, n_total, ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="logs/real-id-sweep.json")
    args = ap.parse_args()

    eps = json.load(open("/tmp/endpoints.json", encoding="utf-8"))
    token = login()
    if not token:
        return 2

    cache = {}
    results = []
    for e in eps:
        if e["http"] != "GET":
            continue
        path = e["path"]
        if not any(path.startswith(p) for p in ROUTE_PREFIXES):
            continue
        if not e["has_var"]:
            continue
        real, filled, total, note = resolve(token, path, cache)
        if filled < total:
            results.append({**e, "probed": real, "status": "SKIP", "note": note, "body": ""})
            continue
        code, payload = get_json(token, real)
        body = json.dumps(payload, ensure_ascii=False)[:300] if payload is not None else ""
        results.append({**e, "probed": real, "status": code, "note": "", "body": body})
        time.sleep(0.03)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    json.dump(results, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    by_mod = defaultdict(Counter)
    bad5, bad4, skipped = [], [], []
    for r in results:
        s = r["status"]
        cls = (str(int(s) // 100) + "xx") if s.isdigit() else s
        by_mod[r["module"]][cls] += 1
        if s == "SKIP":
            skipped.append(r)
        elif cls == "5xx":
            bad5.append(r)
        elif cls == "4xx":
            bad4.append(r)

    print("=== 模块 × 状态分布（真实 ID）===")
    for m in sorted(by_mod):
        print(f"{m:45s} {dict(by_mod[m])}")
    n_ok = sum(1 for r in results if r["status"].startswith("2"))
    print(f"\n探测 {len(results)} 个带变量端点：2xx {n_ok} / 4xx {len(bad4)} / 5xx {len(bad5)} / 未解析 {len(skipped)}")

    print(f"\n=== ★ 真实 5xx（{len(bad5)}）===")
    for r in bad5:
        print(f"500  {r['probed']}  [{r['controller']}]")
        print("      " + r["body"].replace("\n", " ")[:220])

    print(f"\n=== 4xx（{len(bad4)}，需人工判定）===")
    for r in bad4:
        print(f"{r['status']}  {r['probed']}  [{r['controller']}]")

    print(f"\n=== 未能解析真实 ID（{len(skipped)}，这些端点仍未被真正执行）===")
    for r in skipped:
        print(f"SKIP {r['probed']}  [{r['controller']}]  {r['note']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
