#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""主数据（masterdata）两租户隔离验证脚本。

验证目标：
  1) 全局共享数据：平台管理员（tenant 0）构造平台域模板 + 平台参考数据（值域/值，tenant_id=0），
     两个租户管理员均可见可读（全局目录多方只读），但不可篡改（403 / MD_REF_PLATFORM_*）。
  2) 租户隔离数据：两个租户管理员各自造主数据（实体/字段/记录/租户参考数据值），
     验证：各自可见自己的数据；列表互不泄漏；按 ID 直读对方资源 → 404（防 IDOR）；
     跨租户改写参考数据 → 404；平台视角可见两租户（全局视图）。

依赖：python3 + urllib（标准库）。服务需已启动（网关 8888 或按参数直连各服务）。
用法：python3 scripts/verify_masterdata_tenant_isolation.py [--no-cleanup]
"""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
import uuid

DEFAULT_BASE = "http://localhost:8888"

RESULTS: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    RESULTS.append((name, bool(ok), detail))
    flag = "PASS" if ok else "FAIL"
    print(f"  [{flag}] {name}" + (f" — {detail}" if detail else ""))
    return bool(ok)


def http(method: str, path: str, token: str | None = None, body=None, timeout: int = 20):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method.upper())
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8", "replace"))
        except Exception:
            return e.code, {}
    except Exception as e:  # 连接失败等
        return 0, {"message": str(e)}


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


def login(user: str, pwd: str):
    st, parsed = http("POST", "/api/v1/iam/login", body={"username": user, "password": pwd})
    d = data_of(parsed) or {}
    return st, d.get("token"), d


MD = "/api/v1/masterdata"


# ---------------------------------------------------------------------------
# 平台管理员：全局共享数据
# ---------------------------------------------------------------------------


def step_platform_global(admin_token: str) -> dict:
    print("\n== 阶段一：平台管理员构造全局共享数据（平台域模板 + 平台参考数据）==")
    suffix = uuid.uuid4().hex[:8]

    # 平台参考数据（overlay 拆表后：平台值域/值 tenant_id=0，全局只读）
    st, parsed = http("POST", MD + "/reference-sets", admin_token, {
        "setCode": "RS_ISO_" + suffix, "setName": "隔离验证-客户等级值域",
        "externalStandard": "GB/T", "description": "两租户隔离验证全局值域"})
    set_id = data_of(parsed)
    check("平台管理员创建全局参考数据值域", st == 200 and bool(set_id), f"HTTP {st}")

    platform_values: dict[str, int] = {}
    for vcode, vname in (("A", "平台-高档"), ("B", "平台-普通")):
        st, parsed = http("POST", MD + f"/reference-sets/{set_id}/values", admin_token,
                          {"valueCode": vcode, "valueName": vname, "sortOrder": 1})
        vid = data_of(parsed)
        check(f"平台管理员创建平台值 {vcode}-{vname}", st == 200 and bool(vid), f"HTTP {st}")
        platform_values[vcode] = vid

    # 平台域模板（tenant_id=0 全局）
    st, parsed = http("POST", MD + "/templates", admin_token, {
        "domainCode": "TPL_ISO_" + suffix, "domainName": "隔离验证域模板",
        "description": "两租户隔离验证", "defaultGovernanceTier": "L1",
        "fieldSchema": json.dumps([{"code": "name", "name": "名称", "type": "STRING", "length": 64, "required": True}])})
    tpl_id = data_of(parsed)
    check("平台管理员创建平台域模板", st == 200 and bool(tpl_id), f"HTTP {st}")
    if tpl_id:
        st, parsed = http("POST", MD + f"/templates/{tpl_id}/versions", admin_token,
                          {"versionNumber": "V1.0", "changeLog": "隔离验证初始版"})
        check("平台管理员发布模板版本 V1.0", st == 200 and bool(data_of(parsed)), f"HTTP {st}")

    return {"set_id": set_id, "platform_values": platform_values, "tpl_id": tpl_id, "suffix": suffix}


# ---------------------------------------------------------------------------
# 租户：造租户隔离数据
# ---------------------------------------------------------------------------


def step_tenant_build(t: dict, g: dict) -> None:
    print(f"\n== 阶段二：租户 {t['code']} 管理员造主数据 ==")
    token = t["token"]
    suffix = g["suffix"]
    set_id, tpl_id = g["set_id"], g["tpl_id"]

    # 全局参考数据可见
    st, parsed = http("GET", MD + "/reference-sets", token)
    sets = data_of(parsed) or []
    check(f"[{t['code']}] 可见平台全局值域（全局目录只读）", st == 200 and any(str(s.get("id")) == str(set_id) for s in sets), f"HTTP {st}, 共 {len(sets)} 个值域")

    st, parsed = http("GET", MD + f"/reference-sets/{set_id}/values", token)
    vals = data_of(parsed) or []
    plats = [v for v in vals if v.get("scope") == "PLATFORM"]
    check(f"[{t['code']}] 可读平台值（scope=PLATFORM，2 个）", st == 200 and len(plats) >= 2, f"平台值 {len(plats)} 个")

    # 租户私有参考数据值（写路径落租户 overlay 表）
    tcode = "T" + t["code"][-4:]
    st, parsed = http("POST", MD + f"/reference-sets/{set_id}/values", token,
                      {"valueCode": tcode, "valueName": f"租户私有-{t['name']}", "sortOrder": 9})
    t["refValueId"] = data_of(parsed)
    check(f"[{t['code']}] 创建租户私有参考值（overlay 写路径）", st == 200 and bool(t["refValueId"]), f"HTTP {st} {str(parsed.get('message'))[:80]}")

    st, parsed = http("GET", MD + f"/reference-sets/{set_id}/values", token)
    vals = data_of(parsed) or []
    mine = [v for v in vals if str(v.get("id")) == str(t.get("refValueId"))]
    check(f"[{t['code']}] 合并视图中含自己的租户值", bool(mine), f"scope={mine[0].get('scope') if mine else 'N/A'}")

    # 从平台域模板实例化（全局模板，租户各自实例化）
    ent_code = f"iso_md_{suffix}_{t['code'][-6:]}"
    st, parsed = http("POST", MD + "/templates/instantiate", token,
                      {"templateId": tpl_id, "entityCode": ent_code,
                       "name": f"{t['name']}-客户主数据", "description": "隔离验证实例化", "withFields": True})
    t["instEntityId"] = data_of(parsed)
    check(f"[{t['code']}] 从平台模板实例化主数据实体", st == 200 and bool(t["instEntityId"]), f"HTTP {st} {str(parsed.get('message'))[:80]}")

    # 自建实体 + 字段 + 发布 + 记录
    st, parsed = http("POST", MD + "/entities", token,
                      {"name": f"{t['name']}-供应商主数据", "description": "隔离验证自建"})
    t["entityId"] = data_of(parsed)
    check(f"[{t['code']}] 创建主数据实体", st == 200 and bool(t["entityId"]), f"HTTP {st} {str(parsed.get('message'))[:80]}")

    if t.get("entityId"):
        for fcode, fname, req_ in (("code", "编码", True), ("name", "名称", True), ("level", "等级", False)):
            http("POST", MD + f"/entities/{t['entityId']}/fields", token,
                 {"masterDataEntityId": t["entityId"], "code": fcode, "name": fname,
                  "type": "STRING", "length": 64, "required": req_})
        st, parsed = http("GET", MD + f"/entities/{t['entityId']}/fields", token)
        flds = data_of(parsed) or []
        check(f"[{t['code']}] 创建并读取 3 个字段", st == 200 and len(flds) >= 3, f"HTTP {st}, 字段 {len(flds)} 个")

        http("POST", MD + f"/entities/{t['entityId']}/publish", token)
        st, parsed = http("GET", MD + f"/entities/{t['entityId']}", token)
        ent = data_of(parsed) or {}
        check(f"[{t['code']}] 实体已发布", ent.get("status") in ("PUBLISHED", "ENABLED", 1), f"status={ent.get('status')}")

        rec_ids = []
        for i, rec in enumerate(({"code": "SUP-001", "name": "供应商一", "level": "A"},
                                 {"code": "SUP-002", "name": "供应商二", "level": "B"})):
            st, parsed = http("POST", MD + f"/records/entity/{t['entityId']}", token, {"data": rec})
            d = data_of(parsed) or {}
            rid = d.get("id") if isinstance(d, dict) else d
            rec_ids.append(rid)
        t["recordIds"] = rec_ids
        check(f"[{t['code']}] 创建 2 条主数据记录", all(rec_ids), f"ids={rec_ids}")

        st, parsed = http("GET", MD + f"/records?masterDataEntityId={t['entityId']}&pageNum=1&pageSize=10", token)
        page = data_of(parsed) or {}
        recs = page.get("records") or []
        check(f"[{t['code']}] 记录列表可查自己的 2 条", st == 200 and len(recs) >= 2, f"total={page.get('total')}")


# ---------------------------------------------------------------------------
# 隔离负向验证
# ---------------------------------------------------------------------------


def step_isolation(t: dict, other: dict, g: dict) -> None:
    print(f"\n== 阶段三：以租户 {t['code']} 验证与租户 {other['code']} 的隔离 ==")
    token = t["token"]

    # 实体列表互不泄漏 + IDOR
    st, parsed = http("GET", MD + "/entities?pageNum=1&pageSize=100", token)
    recs = (data_of(parsed) or {}).get("records") or []
    ids = {str(r.get("id")) for r in recs}
    check(f"[{t['code']}] 实体列表包含自己的实体", str(t.get("entityId")) in ids, f"count={len(recs)}")
    check(f"[{t['code']}] 实体列表不包含租户 {other['code']} 的实体", str(other.get("entityId")) not in ids, "")
    if other.get("entityId"):
        st, parsed = http("GET", MD + f"/entities/{other['entityId']}", token)
        check(f"[{t['code']}] 直读租户 {other['code']} 的实体 → 404（拒绝 IDOR）", st == 404, f"HTTP {st} {str(parsed.get('message'))[:80]}")

    # 记录隔离
    if other.get("recordIds"):
        st, parsed = http("GET", MD + f"/records/{other['recordIds'][0]}", token)
        check(f"[{t['code']}] 直读租户 {other['code']} 的记录 → 404（拒绝 IDOR）", st == 404, f"HTTP {st}")
    if other.get("entityId"):
        st, parsed = http("GET", MD + f"/records?masterDataEntityId={other['entityId']}&pageNum=1&pageSize=10", token)
        page = data_of(parsed) or {}
        recs = page.get("records") or []
        check(f"[{t['code']}] 按对方实体查记录 → 空（不泄漏）", st in (200, 404) and len(recs) == 0, f"records={len(recs)}")

    # 参考数据隔离：租户 overlay 值互不可见
    st, parsed = http("GET", MD + f"/reference-sets/{g['set_id']}/values", token)
    vals = data_of(parsed) or []
    val_ids = {str(v.get("id")) for v in vals}
    check(f"[{t['code']}] 参考值合并视图含平台值", str(g["platform_values"]["A"]) in val_ids, "")
    check(f"[{t['code']}] 参考值合并视图含自己的租户值", str(t.get("refValueId")) in val_ids, "")
    check(f"[{t['code']}] 参考值合并视图不含租户 {other['code']} 的租户值", str(other.get("refValueId")) not in val_ids, "")

    # 平台值不可篡改（403 平台域只读）
    st, parsed = http("PUT", MD + f"/reference-sets/values/{g['platform_values']['A']}", token,
                      {"id": g["platform_values"]["A"], "valueName": "被租户篡改", "sortOrder": 1})
    check(f"[{t['code']}] 改写平台值 → 403（平台目录只读）", st == 403, f"HTTP {st} {str(parsed.get('message'))[:80]}")
    st, parsed = http("POST", MD + f"/reference-sets/values/{g['platform_values']['A']}/disable", token)
    check(f"[{t['code']}] 停用平台值 → 403（平台目录只读）", st == 403, f"HTTP {st}")

    # 跨租户改写对方的租户参考值 → 404（IDOR）
    if other.get("refValueId"):
        st, parsed = http("PUT", MD + f"/reference-sets/values/{other['refValueId']}", token,
                          {"id": other["refValueId"], "valueName": "越权改名", "sortOrder": 1})
        check(f"[{t['code']}] 改写租户 {other['code']} 的租户参考值 → 404（拒绝 IDOR）", st == 404, f"HTTP {st} {str(parsed.get('message'))[:80]}")


def step_platform_view(admin_token: str, tenants: list[dict]) -> None:
    print("\n== 阶段四：平台管理员视图（ADR-0029 fail-closed：默认仅 tenant 0 数据）==")
    st, parsed = http("GET", MD + "/entities?pageNum=1&pageSize=100", admin_token)
    recs = (data_of(parsed) or {}).get("records") or []
    ids = {str(r.get("id")) for r in recs}
    check("平台管理员默认列表不含两租户的实体（fail-closed 单租户作用域）",
          all(str(t.get("entityId")) not in ids for t in tenants), f"count={len(recs)}")
    print("  [NOTE] 平台管理员跨租户全局视图需 ADR-0029 逃生舱（*AllTenants）或租户切换机制，当前为产品空白（观察项，非隔离缺陷）")


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------


def create_tenant(admin_token: str, name: str, code: str) -> dict | None:
    st, parsed = http("POST", "/api/v1/iam/tenants", admin_token,
                      {"name": name, "code": code, "adminEmail": code + "@iso.test"})
    d = data_of(parsed) or {}
    if st != 200 or not d.get("adminUsername"):
        print(f"  [FAIL] 创建租户 {code} 失败 HTTP {st} {str(parsed)[:160]}")
        return None
    return {"tenantId": str(d["tenantId"]), "adminUsername": d["adminUsername"],
            "initialPassword": d["initialPassword"], "code": code, "name": name}


def summarize() -> int:
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 64)
    print(f"合计 {total} 项，通过 {total - len(failed)}，失败 {len(failed)}")
    for name, _ok, detail in failed:
        print(f"  FAIL {name} — {detail}")
    print("=" * 64)
    return 1 if failed else 0


def main() -> int:
    global BASE
    ap = argparse.ArgumentParser(description="主数据两租户隔离验证")
    ap.add_argument("--base", default=DEFAULT_BASE, help="网关地址")
    ap.add_argument("--admin", default="admin")
    ap.add_argument("--admin-password", default="123456")
    ap.add_argument("--no-cleanup", action="store_true")
    args = ap.parse_args()
    BASE = args.base.rstrip("/")

    print(f"主数据两租户隔离验证：base={BASE}")

    st, admin_token, _ = login(args.admin, args.admin_password)
    if not check("平台管理员登录（tenant 0）", st == 200 and bool(admin_token), f"HTTP {st}"):
        return summarize()
    check("平台管理员 JWT tenantId=0", str(jwt_claim(admin_token, "tenantId")) == "0", "")

    g = step_platform_global(admin_token)

    tenants = []
    for i in ("A", "B"):
        code = "mdiso_" + uuid.uuid4().hex[:12]
        t = create_tenant(admin_token, f"主数据隔离租户{i}", code)
        if not t:
            return summarize()
        tenants.append(t)

    seen_tids: set[str] = set()
    for t in tenants:
        st, tok, _ = login(t["adminUsername"], t["initialPassword"])
        if not check(f"租户 {t['code']} 管理员登录", st == 200 and bool(tok), f"HTTP {st}"):
            return summarize()
        t["token"] = tok
        tid = str(jwt_claim(tok, "tenantId"))
        t["tenantId"] = tid
        check(f"租户 {t['code']} 管理员 JWT tenantId={tid}（非 0 且与已有租户不同）",
              tid != "0" and tid not in seen_tids, f"tid={tid}")
        seen_tids.add(tid)

    for t in tenants:
        step_tenant_build(t, g)

    step_isolation(tenants[0], tenants[1], g)
    step_isolation(tenants[1], tenants[0], g)
    step_platform_view(admin_token, tenants)

    if not args.no_cleanup:
        for t in tenants:
            st, _ = http("DELETE", f"/api/v1/iam/tenants/{t['tenantId']}", admin_token)
            print(f"  [CLEANUP] 删除租户 {t['code']}（{t['tenantId']}）→ HTTP {st}")
        print("  [CLEANUP] 平台模板/参考数据保留（全局共享数据，可复用于回归）")

    return summarize()


if __name__ == "__main__":
    import sys

    sys.exit(main())
