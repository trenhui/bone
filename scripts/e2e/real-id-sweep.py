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

from e2e_lib import endpoints, http, mysql

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
    # 2026-10-30 补齐：见 api-sweep.py 同前缀说明（claims/review/workflow 此前是扫描盲区）。
    "/api/v1/claims",
    "/api/v1/review",
    "/api/v1/workflow",
]

VAR_RE = re.compile(r"\{[^}]+\}")
# 可能的列表容器键
LIST_KEYS = ("list", "records", "content", "items", "rows", "result", "data", "elements", "nodes")
# 可能的 id 键（按优先级）
ID_KEYS = ("id", "recordId", "entityId", "flowId", "code", "entityCode", "name", "typeCode", "dictType")

# 2026-10-31 补齐：变量名 → 候选字段别名表。
# 【为什么必须显式声明】此前只按 `prefer_key == 变量名` 精确匹配，而列表接口实际返回的字段名
# 往往与路径变量不同名（路径写 {entityCode}、实体里只有 code；路径写 {operationId}、列表里只有 id）。
# 结果是 37/81 个带变量端点长期 `unresolved@*` —— 它们**从未被真正执行过**，
# 里面潜藏的 NPE / 类型转换错误 / SQL 错误一概扫不出来（典型「分母缩水的假绿」）。
VAR_ALIASES = {
    "id": ("id", "recordId", "entityId", "flowId", "taskId", "roleId", "userId", "groupId"),
    "entityCode": ("entityCode", "code", "entity", "name"),
    "typeCode": ("typeCode", "code", "dictType", "dictTypeCode"),
    "code": ("code", "typeCode", "dictType"),
    "key": ("key", "configKey", "configName", "name", "code"),
    "objectName": ("objectName", "name", "key", "fileName", "objectKey"),
    "recordId": ("recordId", "id"),
    "fieldId": ("fieldId", "id"),
    "operationId": ("operationId", "id"),
    "claimId": ("claimId", "id", "claimNo", "no"),
    "inspectionNo": ("inspectionNo", "no", "inspectionId", "id"),
    "version": ("version", "versionNo", "ver", "no"),
    "ver": ("ver", "version", "versionNo"),
    "scheduleTaskId": ("scheduleTaskId", "id", "taskId"),
    "logId": ("logId", "id"),
}
# 变量名 → 期望值类型（"num" 只接受数字；"str" 接受字符串）
def _var_kind(name):
    if name in ("id",) or name.endswith("Id"):
        return "num"
    return "str"


def _acceptable(name, v):
    """值 v 能否作为路径变量 name 的回填值（类型口径必须与后端形参一致）。"""
    if v is None:
        return False
    kind = _var_kind(name)
    if kind == "num":
        # 后端形参见 Long：字符串 code（如 e2e340093）会导致 400「参数 id 格式不正确」假阳性
        if isinstance(v, bool):
            return False
        if isinstance(v, int):
            return v > 0
        if isinstance(v, str):
            return v.strip().isdigit() and int(v) > 0
        return False
    if isinstance(v, (int, float)) and not isinstance(v, bool):
        return True
    return isinstance(v, str) and bool(v.strip()) and len(v) < 128


# 2026-10-31 补齐：活库兜底解析。
# 【为什么还要这一层】有些集合根本没有「列表接口」（`/system/dict/types` GET 直接 405），
# 或者列表接口有默认过滤（`/claims` 空 statuses 默认只看 PENDING_ADJUST，本环境 0 条）
# ⇒ 走 API 永远拿不到真实 ID，这些端点**从未被真正执行过**。
# 活库是第二真源（与 e2e_lib/mysql.py 的「双证据源」同口径），只读 SELECT，不改数据。
DB_HINTS = [
    # (路径包含片段, 变量名, 只读 SQL)
    ("/system/dict/types/", "code",
     "SELECT code FROM sys_dict_type WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/system/dict/items/", "typeCode",
     "SELECT code FROM sys_dict_type WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/runtime/entities/", "entityCode",
     "SELECT code FROM meta_entity WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/runtime/approval/records/", "entityCode",
     "SELECT code FROM meta_entity WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/workflow/definitions/", "key",
     "SELECT def_key FROM wf_process_def WHERE enabled=1 ORDER BY id LIMIT 1"),
    ("/metadata/fields/", "fieldId",
     "SELECT id FROM meta_field WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/claims/", "id",
     "SELECT id FROM t_claim_case WHERE tenant_id=0 AND deleted=0 ORDER BY id LIMIT 1"),
    ("/claims/", "claimId",
     "SELECT id FROM t_claim_case WHERE tenant_id=0 AND deleted=0 ORDER BY id LIMIT 1"),
    ("/review/inspections/", "inspectionNo",
     "SELECT inspection_no FROM t_claim_inspection ORDER BY id LIMIT 1"),
    ("/system/schedule-tasks/", "id",
     "SELECT id FROM sys_schedule_task WHERE deleted=0 ORDER BY id LIMIT 1"),
    ("/system/logs/", "id",
     "SELECT id FROM sys_log ORDER BY id LIMIT 1"),
    ("/masterdata/quality/rules/", "id",
     "SELECT id FROM mdm_quality_rule WHERE deleted=0 ORDER BY id LIMIT 1"),
]


def db_resolve(path, name):
    """活库兜底：按 (路径片段, 变量名) 查一条真实值；查不到返回 None。"""
    for frag, vname, sql in DB_HINTS:
        if frag in path and vname == name:
            out, err = mysql.query(sql)
            if not out:
                continue
            # mysql -e 输出首行是列名，取第二行第一列
            lines = [ln for ln in out.splitlines() if ln.strip()]
            if len(lines) < 2:
                continue
            val = lines[1].split("\t")[0].strip()
            if val and val != "NULL":
                return val
    return None


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

    def from_obj(obj, depth=0):
        if not isinstance(obj, dict) or depth > 3:
            return None
        # 1) 变量名 → 别名表命中（最可靠：字段名与路径变量不同名也能对上）
        if prefer_key:
            for k in VAR_ALIASES.get(prefer_key, (prefer_key,)):
                v = obj.get(k)
                if _acceptable(prefer_key, v):
                    return v
        # 2) 兜底：按类型口径从通用 id 键里挑
        for k in ID_KEYS:
            v = obj.get(k)
            if prefer_key:
                if _acceptable(prefer_key, v):
                    return v
            elif isinstance(v, int) and v > 0:
                return v
            elif isinstance(v, str) and v.strip() and len(v) < 128 and k in (
                "code", "entityCode", "name", "typeCode", "dictType",
            ):
                return v
        # 3) 再深一层：容器键（遍历前若干个元素，别只看 sub[0]——首个元素常缺目标字段）
        for k in LIST_KEYS:
            sub = obj.get(k)
            if isinstance(sub, list):
                for it in sub[:10]:
                    r = from_obj(it, depth + 1)
                    if r is not None:
                        return r
            elif isinstance(sub, dict):
                r = from_obj(sub, depth + 1)
                if r is not None:
                    return r
        return None

    if isinstance(data, list):
        for it in data[:10]:
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
            val = None
            # 候选集合前缀：路径前缀本身，以及逐级去掉末段后的父集合。
            # （`/system/config/key/{key}` 这类「非集合前缀」只有回退到 `/system/config` 才拿得到列表）
            cands = [prefix]
            seg = prefix
            for _ in range(2):
                seg = seg.rsplit("/", 1)[0] if "/" in seg else ""
                if seg and seg != prefix:
                    cands.append(seg)
            for cand in cands:
                for extra in ("", "?page=1&size=20"):
                    code, payload = get_json(token, cand + extra)
                    if code.startswith("2") and payload is not None:
                        val = pick_id(payload, name)
                        if val is not None:
                            break
                if val is not None:
                    break
            if val is None:
                # 列表接口缺失 / 有默认过滤 ⇒ 退回活库（只读）
                val = db_resolve(path, name)
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

    # 端点真源 = Controller 源码（不再读 /tmp 缓存快照，详见 e2e_lib/endpoints.py 说明）
    eps = endpoints.load()
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
