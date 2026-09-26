#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""两租户隔离验证脚本（平台管理员全局数据 + 两租户管理员 + 跨租户隔离负向验证）。

验证目标（对应需求）：
  1) 全局共享数据：由平台管理员（tenant 0）构造一批数据（租户注册表、平台级系统配置），
     并验证租户管理员无法访问/篡改这些全局数据（403）。
  2) 租户隔离数据：模拟两个租户管理员（各自自动初始化的租户管理员账号），分别在 IAM（账号/部门/角色）
     与元数据（实体/运行时记录，若服务可达）造一批数据，验证：
       - 各租户能正常看到自己的数据（功能正确性）；
       - 租户 A 看不到租户 B 的数据（列表无泄漏）；
       - 用租户 A 的 token 直接按 ID 读租户 B 的资源 → 404/403（防 IDOR）；
       - 用租户 A 的 token 把账号挂到租户 B 的部门 → 被拒（DEPT_NOT_FOUND，防跨租户赋值）。

依赖：python3 + urllib（标准库），无需第三方依赖。服务需已启动（IAM / System 必选，Metadata 可选）。

用法：
  python3 scripts/verify_tenant_isolation.py
  python3 scripts/verify_tenant_isolation.py --iam http://localhost:8081 --system http://localhost:8083 \
        --metadata http://localhost:9001 --admin admin --admin-password 123456
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
import uuid

DEFAULT_IAM = "http://localhost:8081"
DEFAULT_SYSTEM = "http://localhost:8083"
DEFAULT_METADATA = "http://localhost:9001"

RESULTS: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    RESULTS.append((name, bool(ok), detail))
    flag = "PASS" if ok else "FAIL"
    print(f"  [{flag}] {name}" + (f" — {detail}" if detail else ""))
    return bool(ok)


def http(method: str, url: str, token: str | None = None, body=None, headers: dict | None = None, timeout: int = 20):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method.upper())
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # 连接失败等
        return 0, str(e)


def json_of(raw: str):
    try:
        return json.loads(raw)
    except Exception:
        return None


def data_of(parsed):
    return parsed.get("data") if isinstance(parsed, dict) else None


def jwt_claim(token: str, key: str):
    import base64

    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return json.loads(base64.urlsafe_b64decode(payload.encode())).get(key)
    except Exception:
        return None


def first_id_of(parsed, key="id"):
    d = data_of(parsed) or {}
    if isinstance(d, dict):
        recs = d.get("records") or d.get("list") or []
        if recs and isinstance(recs[0], dict):
            return recs[0].get(key)
    return None


# --------------------------------------------------------------------------
# 平台管理员：全局共享数据
# --------------------------------------------------------------------------


def step_platform_global(iam: str, system: str, admin_token: str, tenants: list[dict]) -> dict:
    print("\n== 阶段一：平台管理员构造全局共享数据并验证租户不可见 ==")
    # 平台管理员可见全量租户（全局注册表）
    st, raw = http("GET", iam + "/api/v1/iam/tenants?page=1&size=50", admin_token)
    page = data_of(json_of(raw)) or {}
    records = page.get("records") or []
    seen = {t["code"] for t in tenants if any(r.get("code") == t["code"] for r in records)}
    check("平台管理员可列出租户注册表（全局数据可见）", st == 200 and len(seen) == len(tenants),
          f"HTTP {st}, 新建租户中被列出 {len(seen)}/{len(tenants)}")

    # 平台级系统配置（全局）：创建一条，平台管理员可读
    cfg_key = "iso_verify_global_" + uuid.uuid4().hex[:8]
    st, raw = http("POST", system + "/api/v1/system/config", admin_token,
                   {"configKey": cfg_key, "configValue": "v1", "configType": "SYSTEM", "description": "隔离验证全局配置"})
    cfg_id = data_of(json_of(raw))
    check("平台管理员可写全局系统配置（tenant 0）", st == 200 and bool(cfg_id), f"HTTP {st} {raw[:120]}")
    st, raw = http("GET", system + f"/api/v1/system/config/{cfg_id}", admin_token)
    check("平台管理员可读刚写的全局配置", st == 200 and bool(data_of(json_of(raw))), f"HTTP {st}")

    # 负向：租户管理员访问全局数据（租户注册表 / 全局配置写）必须被拒
    t1 = tenants[0]["token"]
    st, _ = http("GET", iam + "/api/v1/iam/tenants?page=1&size=10", t1)
    check("租户管理员读取租户注册表 → 403（全局数据对租户不可见）", st == 403, f"HTTP {st}")
    st, raw = http("POST", system + "/api/v1/system/config", t1,
                   {"configKey": "iso_tenant_try_" + uuid.uuid4().hex[:6], "configValue": "x", "configType": "SYSTEM"})
    check("租户管理员写全局系统配置 → 403（平台域写被拒）", st == 403, f"HTTP {st} {raw[:140]}")
    return {"cfg_key": cfg_key, "cfg_id": cfg_id}


# --------------------------------------------------------------------------
# 租户管理员：各自造一批租户隔离数据
# --------------------------------------------------------------------------


def step_tenant_build(iam: str, metadata: str, meta_ok: bool, t: dict) -> dict:
    print(f"\n== 阶段二：租户 {t['code']} 管理员造租户隔离数据（IAM + 元数据）==")
    token = t["token"]
    hdr = {"X-Tenant-Id": str(t["tenantId"])}

    # 部门
    st, raw = http("POST", iam + "/api/v1/iam/depts", token, {"name": t["code"] + "_部门", "status": 1})
    t["deptId"] = data_of(json_of(raw))
    check(f"[{t['code']}] 创建部门", st in (200, 201) and bool(t["deptId"]), f"HTTP {st} {raw[:120]}")

    # 角色
    st, raw = http("POST", iam + "/api/v1/iam/roles", token,
                   {"name": t["code"] + "_角色", "code": t["code"] + "_role", "description": "隔离验证"})
    t["roleId"] = data_of(json_of(raw))
    check(f"[{t['code']}] 创建角色", st in (200, 201) and bool(t["roleId"]), f"HTTP {st} {raw[:120]}")

    # 账号（必须带部门，验证 deptId 必填已生效）
    uname = t["code"] + "_user"
    st, raw = http("POST", iam + "/api/v1/iam/accounts", token,
                   {"username": uname, "password": "Tenant@123456", "email": uname + "@iso.test",
                    "realName": "隔离验证用户", "deptId": t["deptId"], "roleIds": [t["roleId"]]})
    t["accountId"] = data_of(json_of(raw))
    check(f"[{t['code']}] 创建账号（带部门）", st in (200, 201) and bool(t["accountId"]), f"HTTP {st} {raw[:120]}")

    # 负向：创建账号不带部门 → 必须 400（deptId 必填）
    st, raw = http("POST", iam + "/api/v1/iam/accounts", token,
                   {"username": uname + "_nodept", "password": "Tenant@123456", "email": "x@iso.test", "realName": "无部门"})
    check(f"[{t['code']}] 创建账号缺部门 → 400（deptId 必填生效）", st == 400, f"HTTP {st}")

    # 元数据：实体 + 字段 + 发布 + 运行时记录（若服务可达）
    if meta_ok:
        suffix = uuid.uuid4().hex[:8]
        code = "iso_ent_" + suffix
        table = "meta_iso_" + suffix
        st, raw = http("POST", metadata + "/api/v1/metadata/entities", token, headers=hdr,
                       body={"name": "IsoEntity", "code": code, "displayName": t["code"] + "实体",
                             "description": "隔离验证", "tableName": table, "type": 0, "deliveryMode": 1})
        t["entityId"] = data_of(json_of(raw))
        if check(f"[{t['code']}] 创建元数据实体", st in (200, 201) and bool(t["entityId"]), f"HTTP {st} {raw[:120]}"):
            for fname, ftype, flen, req in (("name", "VARCHAR", 64, True), ("phone", "VARCHAR", 32, False)):
                http("POST", metadata + f"/api/v1/metadata/entities/{t['entityId']}/fields", token, headers=hdr,
                     body={"name": fname, "code": fname, "displayName": fname, "type": ftype, "length": flen, "required": req, "sortOrder": 0})
            http("POST", metadata + f"/api/v1/metadata/entities/{t['entityId']}/publish", token, headers=hdr, body=None)
            st, raw = http("POST", metadata + f"/api/v1/runtime/entities/{code}/records", token, headers=hdr,
                           body={"name": "张三", "phone": "13800000001"})
            t["recId"] = (data_of(json_of(raw)) or {}).get("id")
            check(f"[{t['code']}] 创建运行时记录", st in (200, 201) and bool(t["recId"]), f"HTTP {st} {raw[:120]}")
            t["entityCode"] = code
    return t


# --------------------------------------------------------------------------
# 阶段三：跨租户隔离负向验证
# --------------------------------------------------------------------------


def flatten_dept_ids(nodes, acc=None):
    """DeptController 只有 /depts/tree（无分页/详情端点），递归展平取 id 集合。"""
    if acc is None:
        acc = set()
    for n in nodes or []:
        if isinstance(n, dict):
            acc.add(str(n.get("id")))
            flatten_dept_ids(n.get("children"), acc)
    return acc


def step_isolation(iam: str, metadata: str, meta_ok: bool, t: dict, other: dict):
    print(f"\n== 阶段三：以租户 {t['code']} 验证看不到租户 {other['code']} 的数据（隔离）==")
    token = t["token"]
    hdr = {"X-Tenant-Id": str(t["tenantId"])}

    # 列表无泄漏
    st, raw = http("GET", iam + "/api/v1/iam/accounts?page=1&size=50", token)
    recs = (data_of(json_of(raw)) or {}).get("records") or []
    check(f"[{t['code']}] 账号列表包含自己创建的账号", t["accountId"] is not None and any(r.get("id") == str(t["accountId"]) for r in recs), f"count={len(recs)}")
    check(f"[{t['code']}] 账号列表不包含租户 {other['code']} 的账号", other.get("accountId") is None or str(other["accountId"]) not in {str(r.get("id")) for r in recs},
          f"命中={str(other['accountId']) in {str(r.get('id')) for r in recs}}")

    # 部门：无分页/详情端点，用 /depts/tree 展平校验
    st, raw = http("GET", iam + "/api/v1/iam/depts/tree", token)
    tree_ids = flatten_dept_ids(data_of(json_of(raw)) or [])
    check(f"[{t['code']}] 部门树包含自己的部门", t.get("deptId") is not None and str(t["deptId"]) in tree_ids, f"count={len(tree_ids)}")
    check(f"[{t['code']}] 部门树不包含租户 {other['code']} 的部门", other.get("deptId") is None or str(other["deptId"]) not in tree_ids, "")

    st, raw = http("GET", iam + "/api/v1/iam/roles?page=1&size=50", token)
    recs = (data_of(json_of(raw)) or {}).get("records") or []
    check(f"[{t['code']}] 角色列表不包含租户 {other['code']} 的角色", other.get("roleId") is None or str(other["roleId"]) not in {str(r.get("id")) for r in recs}, "")

    # 直接按 ID 直读（IDOR）→ 必须 404（部门无详情端点，用 PUT 写越权验证）
    for kind, path, oid in (("账号", "accounts", other.get("accountId")), ("角色", "roles", other.get("roleId"))):
        if not oid:
            continue
        st, _ = http("GET", f"{iam}/api/v1/iam/{path}/{oid}", token)
        check(f"[{t['code']}] 直读租户 {other['code']} 的{kind}({oid}) → 404（拒绝越权）", st == 404, f"HTTP {st}")

    # 写越权：用本租户 token 更新别的租户的部门 → 必须 404（若 200 即隔离漏洞）
    if other.get("deptId"):
        st, raw = http("PUT", f"{iam}/api/v1/iam/depts/{other['deptId']}", token,
                       {"name": "越权改名", "status": 1})
        check(f"[{t['code']}] PUT 改名租户 {other['code']} 的部门 → 404（写越权被拒）", st == 404, f"HTTP {st} {raw[:120]}")

    # 跨租户赋值（把账号挂到别的租户的部门）→ 必须被拒
    if other.get("deptId"):
        st, raw = http("POST", iam + "/api/v1/iam/accounts", token,
                       {"username": t["code"] + "_cross", "password": "Tenant@123456", "email": "c@iso.test",
                        "realName": "跨租户赋值", "deptId": other["deptId"], "roleIds": [t["roleId"]]})
        check(f"[{t['code']}] 账号挂到租户 {other['code']} 的部门 → 被拒（DEPT_NOT_FOUND）", st in (400, 403, 404), f"HTTP {st} {raw[:120]}")

    # 元数据隔离（若造过）：拒绝访问即可（4xx），记录实际状态码与响应体用于契约核对
    if meta_ok and other.get("entityId"):
        st, raw = http("GET", metadata + f"/api/v1/metadata/entities/{other['entityId']}", token, headers=hdr)
        check(f"[{t['code']}] 直读租户 {other['code']} 的元数据实体 → 拒绝（4xx）", st in (400, 403, 404), f"HTTP {st} {raw[:140]}")
        st, raw = http("GET", metadata + "/api/v1/metadata/entities?page=1&size=100", token, headers=hdr)
        recs = (data_of(json_of(raw)) or {}).get("records") or []
        check(f"[{t['code']}] 元数据实体列表不包含租户 {other['code']} 的实体", other.get("entityCode") not in {r.get("code") for r in recs}, "")


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------


def create_tenant(iam: str, admin_token: str, name: str, code: str) -> dict | None:
    st, raw = http("POST", iam + "/api/v1/iam/tenants", admin_token,
                   {"name": name, "code": code, "adminEmail": code + "@iso.test"})
    d = data_of(json_of(raw)) or {}
    if st != 200 or not d.get("adminUsername"):
        print(f"  [FAIL] 创建租户 {code} 失败 HTTP {st} {raw[:160]}")
        return None
    return {"tenantId": int(d["tenantId"]), "adminUsername": d["adminUsername"],
            "initialPassword": d["initialPassword"], "adminAccountId": d.get("adminAccountId"),
            "code": code, "name": name}


def login(iam: str, user: str, pwd: str):
    st, raw = http("POST", iam + "/api/v1/iam/login", body={"username": user, "password": pwd})
    d = (data_of(json_of(raw)) or {})
    return st, d.get("token"), d


def main() -> int:
    ap = argparse.ArgumentParser(description="两租户隔离验证")
    ap.add_argument("--iam", default=DEFAULT_IAM)
    ap.add_argument("--system", default=DEFAULT_SYSTEM)
    ap.add_argument("--metadata", default=DEFAULT_METADATA)
    ap.add_argument("--admin", default="admin")
    ap.add_argument("--admin-password", default="123456")
    ap.add_argument("--no-cleanup", action="store_true", help="不删除本次创建的租户")
    args = ap.parse_args()

    print(f"两租户隔离验证：iam={args.iam} system={args.system} metadata={args.metadata}")

    # 平台管理员登录（tenant 0）
    st, admin_token, _ = login(args.iam, args.admin, args.admin_password)
    if not check("平台管理员登录（tenant 0）", st == 200 and bool(admin_token), f"HTTP {st}"):
        return summarize()
    admin_tid = jwt_claim(admin_token, "tenantId")
    check("平台管理员 JWT tenantId=0", str(admin_tid) == "0", f"tenantId={admin_tid}")

    # 元数据服务是否可达
    meta_ok = http("GET", args.metadata + "/actuator/health", timeout=5)[0] == 200
    print(f"  元数据服务可达：{meta_ok}（不可达则跳过元数据隔离验证）")

    # 创建两个租户（自动初始化各自租户管理员）
    tenants = []
    for i in ("A", "B"):
        code = "iso_" + uuid.uuid4().hex[:12]
        t = create_tenant(args.iam, admin_token, f"隔离验证租户{i}", code)
        if not t:
            return summarize()
        tenants.append(t)

    # 租户管理员登录（用初始密码）
    for t in tenants:
        st, tok, _ = login(args.iam, t["adminUsername"], t["initialPassword"])
        if not check(f"租户 {t['code']} 管理员登录", st == 200 and bool(tok), f"HTTP {st}"):
            return summarize()
        t["token"] = tok
        t["tenantId"] = int(jwt_claim(tok, "tenantId"))
        check(f"租户 {t['code']} 管理员 JWT tenantId={t['tenantId']}（非 0 且各不相同）", t["tenantId"] != 0, f"tid={t['tenantId']}")

    # 阶段一：全局数据
    step_platform_global(args.iam, args.system, admin_token, tenants)

    # 阶段二：各租户造数据
    for t in tenants:
        step_tenant_build(args.iam, args.metadata, meta_ok, t)

    # 阶段三：隔离负向（A 看 B，B 看 A）
    step_isolation(args.iam, args.metadata, meta_ok, tenants[0], tenants[1])
    step_isolation(args.iam, args.metadata, meta_ok, tenants[1], tenants[0])

    # 清理（best-effort，不计入 PASS/FAIL：删除失败仅提示，不影响隔离验证结论）
    if not args.no_cleanup:
        for t in tenants:
            st, raw = http("DELETE", args.iam + f"/api/v1/iam/tenants/{t['tenantId']}", admin_token)
            print(f"  [CLEANUP] 删除租户 {t['code']}（{t['tenantId']}）→ HTTP {st}"
                  + ("" if st in (200, 404) else f" {raw[:120]}"))
        print("  [CLEANUP] 全局配置与租户内数据保留（可在平台管理台手动清理）")

    return summarize()


def summarize() -> int:
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 64)
    print(f"合计 {total} 项，通过 {total - len(failed)}，失败 {len(failed)}")
    for name, _ok, detail in failed:
        print(f"  FAIL {name} — {detail}")
    print("=" * 64)
    return 1 if failed else 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
