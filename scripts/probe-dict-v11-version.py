#!/usr/bin/env python3
"""字典打通 v1.1 — A 项（/options 版本协商 + 投影缓存 SWR 自动刷新）E2E 探针。

直达服务（免网关，gateway 未起）：
  iam      127.0.0.1:8081  （登录取 token）
  system   127.0.0.1:8083  （/api/v1/system/dict/types/{code}/version）
  metadata 127.0.0.1:9001  （/api/v1/metadata/fields/{id}/options/version + 物化/漂移）

验证判据（每条锚定「删掉实现会红」的性质）：
  T1  system /version 返回业务版本号 V0（值域内容 max(updatedAt) epoch millis）
  T2  绑定字段并刷新后，metadata 的 sourceVersion == snapshotVersion（初始即同步基线）
  T3  真源新增项后，system /version 递增为 V1 > V0（版本判据确实随内容变更递增）
  T4  真源变更后，metadata sourceVersion(V1) != snapshotVersion(V0)（SWR/版本协商检出漂移）
  T5  option-diff 仍能实时给出 added=[D]（解析器在版本变化后确实重取真源，非陈旧缓存）
  T6  再次刷新后，metadata snapshotVersion 追上 V1（SWR 在版本变化时重物化，重新同步）
  T7  二次刷新无真源变更时 snapshotVersion 仍 == sourceVersion（版本未变不退化，不引入漂移）
  T8  负向：version 端点对不存在值域返回 0（空值域的合法版本基线），且不为负、不抛 500

注：版本号按项目约定序列化为字符串（Long→string），探针统一 int() 比较。
"""
import json
import sys
import time
import urllib.request
import urllib.error

IAM = "http://127.0.0.1:8081"
SYS = "http://127.0.0.1:8083"
META = "http://127.0.0.1:9001"

_RUN = time.strftime("%Y%m%d_%H%M%S")
TYPE_CODE = f"E2E_VER_{_RUN}"
ENTITY_CODE = f"e2e_ver_entity_{_RUN}"

passed = []
failed = []


def check(name, cond, detail=""):
    (passed if cond else failed).append((name, detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f" — {detail}" if detail and not cond else ""))


def to_int(v):
    return int(v) if v is not None else None


# 网关在每个入站请求上盖章 X-Tenant-Id；直连服务（绕过网关）须自行携带，
# 否则 metadata 的 TenantContextInterceptor 不建立租户上下文 → SDK 失败关闭 → 500。
TENANT_HEADER = ("X-Tenant-Id", "0")


def call(method, base, path, body=None, token=None, expect=None):
    req = urllib.request.Request(base + path, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header(*TENANT_HEADER)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data, timeout=15) as resp:
            payload = json.loads(resp.read().decode())
            code = resp.status
    except urllib.error.HTTPError as e:
        payload = json.loads(e.read().decode() or "{}")
        code = e.code
    if expect is not None:
        allowed = expect if isinstance(expect, (tuple, list, set)) else (expect,)
        if code not in allowed:
            raise AssertionError(f"{method} {base}{path} => HTTP {code} (expect {allowed}): {payload}")
    return code, payload


def extract_id(resp):
    d = resp.get("data")
    if isinstance(d, dict):
        return d.get("id") or d.get("fieldId") or d.get("entityId")
    return d


def main():
    # ---------- 登录（前置失败即中止，禁假绿） ----------
    _, resp = call("POST", IAM, "/api/v1/iam/login", {"username": "admin", "password": "123456"})
    token = (resp.get("data") or {}).get("token")
    if not token:
        print("FATAL: 登录失败，探针中止")
        sys.exit(1)
    print(f"[setup] 登录成功（admin tenant 链路就绪）")

    # ---------- T1：system 版本端点基线 V0 ----------
    print("\n=== T1 版本端点基线（system） ===")
    call("POST", SYS, "/api/v1/system/dict/types", {
        "code": TYPE_CODE, "name": "E2E版本协商", "category": "LIST",
        "valueType": "STRING", "status": 1, "sort": 0,
    }, token, expect=200)
    ids = {}
    for c in ("A", "B", "C"):
        _, r = call("POST", SYS, "/api/v1/system/dict/items", {
            "typeCode": TYPE_CODE, "code": c, "label": f"标签{c}", "value": c,
            "status": 1, "sort": ord(c) - 64,
        }, token, expect=200)
        ids[c] = extract_id(r)
    _, rv = call("GET", SYS, f"/api/v1/system/dict/types/{TYPE_CODE}/version", token=token)
    V0 = to_int(rv.get("data"))
    check("T1 system /version 返回 V0（epoch millis > 1e12）",
          V0 is not None and V0 > 1_000_000_000_000, f"V0={V0}")

    # ---------- T2：绑定 + 刷新 → source/snapshot 同步基线 ----------
    print("\n=== T2 绑定物化 + 版本同步基线（metadata） ===")
    _, r = call("POST", META, "/api/v1/metadata/entities", {
        "name": "E2E版本协商实体", "code": ENTITY_CODE, "displayName": "E2E版本协商实体",
        "tableName": "t_" + ENTITY_CODE, "type": 1, "deliveryMode": 1,
    }, token, expect=(200, 201))
    entity_id = extract_id(r)
    _, r = call("POST", META, f"/api/v1/metadata/entities/{entity_id}/fields", {
        "name": "标签", "code": "label", "displayName": "标签", "type": "ENUM",
        "enumSource": json.dumps({"type": "dict", "mode": "snapshot", "ref": {"typeCode": TYPE_CODE}}),
    }, token, expect=(200, 201))
    field_id = extract_id(r)
    print(f"[setup] 字段 {field_id} 绑定 {TYPE_CODE}")
    call("POST", META, f"/api/v1/metadata/fields/{field_id}/options/refresh", {}, token, expect=200)
    _, rv = call("GET", META, f"/api/v1/metadata/fields/{field_id}/options/version", token=token)
    ov = rv.get("data") or {}
    sv0 = to_int(ov.get("sourceVersion"))
    snap0 = to_int(ov.get("snapshotVersion"))
    check("T2.1 sourceVersion == V0（物化时写入真源版本）", sv0 == V0, f"source={sv0} V0={V0}")
    check("T2.2 初始快照即与真源同步（source==snapshot）", sv0 == snap0, f"source={sv0} snapshot={snap0}")

    # ---------- T3：真源新增项 → 版本递增 V1 > V0 ----------
    print("\n=== T3 真源变更 → 版本递增（system） ===")
    call("POST", SYS, "/api/v1/system/dict/items", {
        "typeCode": TYPE_CODE, "code": "D", "label": "标签D", "value": "D", "status": 1, "sort": 4,
    }, token, expect=200)
    _, rv = call("GET", SYS, f"/api/v1/system/dict/types/{TYPE_CODE}/version", token=token)
    V1 = to_int(rv.get("data"))
    check("T3 system /version 随内容变更递增 V1 > V0", V1 is not None and V1 > V0, f"V0={V0} V1={V1}")

    # ---------- T4：版本协商检出漂移（source!=snapshot） ----------
    print("\n=== T4 版本协商检出漂移（metadata） ===")
    _, rv = call("GET", META, f"/api/v1/metadata/fields/{field_id}/options/version", token=token)
    ov = rv.get("data") or {}
    sv1 = to_int(ov.get("sourceVersion"))
    snap1 = to_int(ov.get("snapshotVersion"))
    check("T4.1 sourceVersion 已升到 V1", sv1 == V1, f"source={sv1} V1={V1}")
    check("T4.2 snapshot 未刷新 → source != snapshot（漂移被版本协商检出）",
          sv1 == V1 and snap1 == snap0 and sv1 != snap1,
          f"source={sv1} snapshot={snap1}")

    # ---------- T5：解析器在版本变化后确实重取真源（非陈旧缓存） ----------
    _, r = call("GET", META, f"/api/v1/metadata/fields/{field_id}/option-diff", token=token)
    diff = r.get("data") or {}
    check("T5 option-diff 实时给出 added=[D]（解析器重取真源，未用旧缓存）",
          diff.get("added") == ["D"], f"added={diff.get('added')}")

    # ---------- T6：刷新后快照追上 V1（SWR 在版本变化时重物化） ----------
    print("\n=== T6 刷新重新同步（metadata） ===")
    call("POST", META, f"/api/v1/metadata/fields/{field_id}/options/refresh", {}, token, expect=200)
    _, rv = call("GET", META, f"/api/v1/metadata/fields/{field_id}/options/version", token=token)
    ov = rv.get("data") or {}
    sv2 = to_int(ov.get("sourceVersion"))
    snap2 = to_int(ov.get("snapshotVersion"))
    check("T6 刷新后 snapshotVersion 追上 V1（SWR 重物化成功）", sv2 == V1 and snap2 == V1,
          f"source={sv2} snapshot={snap2} V1={V1}")

    # ---------- T7：无变更二次刷新不退化、不引入漂移 ----------
    print("\n=== T7 版本未变不退化（metadata） ===")
    call("POST", META, f"/api/v1/metadata/fields/{field_id}/options/refresh", {}, token, expect=200)
    _, rv = call("GET", META, f"/api/v1/metadata/fields/{field_id}/options/version", token=token)
    ov = rv.get("data") or {}
    check("T7 无真源变更时刷新仍 source==snapshot（SWR 不退化、不误报漂移）",
          to_int(ov.get("sourceVersion")) == to_int(ov.get("snapshotVersion")) == V1, f"ov={ov}")

    # ---------- T8：负向 — 不存在值域版本基线为 0 ----------
    print("\n=== T8 负向：空/不存在值域版本基线 ===")
    _, rv = call("GET", SYS, f"/api/v1/system/dict/types/NO_SUCH_TYPE_{_RUN}/version", token=token)
    nv = to_int(rv.get("data"))
    check("T8 不存在值域 /version 返回 0（合法基线，非 500/负数）", nv == 0, f"data={nv}")

    # ---------- 汇总 ----------
    print(f"\n=== 汇总：{len(passed)} passed, {len(failed)} failed ===")
    for name, detail in failed:
        print(f"  FAIL: {name} — {detail}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
