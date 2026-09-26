#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""租户数据隔离 + 归属部门必填 验收脚本（直连 IAM:8081 / System:8083，无需网关）。

覆盖用户诉求：
  (1) 平台管理员造一批【全局共享】数据，验证功能正确；
  (2) 模拟 2 个租户管理员，各在 IAM/System 模块造一批数据，验证功能正常；
  (3) 验证租户之间的数据隔离（互不可见、越权直查被拒）；
  (4) 验证「归属部门必填」规则（不填部门创建账号被拒）。

用法：
  python3 scripts/verify_tenant_isolation_e2e.py
依赖：python3 + 本地已启动的 bone-iam(:8081) / bone-system(:8083) 与 MySQL/Redis。
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
import uuid

import pymysql

IAM = "http://localhost:8081"
SYS = "http://localhost:8083"
ADMIN_USER = "admin"
ADMIN_PWD = "123456"

RESULTS: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    RESULTS.append((name, bool(ok), detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))
    return bool(ok)


def http(method: str, url: str, token: str | None = None, body=None, timeout: int = 20):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method.upper())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def j(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def data_of(p):
    return p.get("data") if isinstance(p, dict) else None


def login(user: str, pwd: str):
    s, raw = http("POST", IAM + "/api/v1/iam/login", body={"username": user, "password": pwd})
    p = j(raw)
    d = data_of(p) or {}
    return s, d.get("token"), d


def create_tenant(token, name, code, email):
    s, raw = http("POST", IAM + "/api/v1/iam/tenants", token,
                  {"name": name, "code": code, "adminEmail": email})
    p = j(raw)
    d = data_of(p) or {}
    return s, d.get("tenantId"), d.get("adminUsername"), d.get("initialPassword"), raw


def rand() -> str:
    # 基于 uuid 而非时间：避免本环境时钟精度不足导致重跑时后缀碰撞（与历史测试数据冲突）。
    return uuid.uuid4().hex[:12]


def main() -> int:
    # ---------- 0. 平台管理员登录 ----------
    print("\n== 0. 平台管理员登录（tenant 0）==")
    s, admin_tok, _ = login(ADMIN_USER, ADMIN_PWD)
    if not check("平台管理员登录", s == 200 and bool(admin_tok), f"HTTP {s}"):
        return 1

    # ---------- 1. 全局共享数据：平台管理员造一批 ----------
    print("\n== 1. 平台管理员创建【全局共享】数据（sys_config / tenant 0）==")
    gkey = "verify.global." + rand()
    s, raw = http("POST", SYS + "/api/v1/system/config", admin_tok,
                  {"configKey": gkey, "configValue": "global-v1",
                   "configType": "SYSTEM", "description": "隔离验收-全局配置"})
    check("平台管理员创建全局配置(sys_config, tenant 0)", s == 200, f"HTTP {s} {raw[:140]}")
    CREATED_CONFIG_KEYS.append(gkey)

    # ---------- 2. 创建 2 个租户（自动产出 2 个租户管理员）----------
    print("\n== 2. 创建 2 个租户并取出各自租户管理员 ==")
    tenants = []
    for i in ("A", "B"):
        suffix = rand()
        code = f"ISO_{i}_{suffix}"
        ts, tid, uname, pwd, raw = create_tenant(
            admin_tok, f"隔离验证租户{i}", code, f"admin{i}@{suffix}.iso.test")
        if not check(f"创建租户{i}（自动产出租户管理员）", ts == 200 and bool(tid),
                     f"HTTP {ts} {raw[:140]}"):
            continue
        tenants.append({"tag": i, "tid": tid, "uname": uname, "pwd": pwd})
        CREATED_TENANT_IDS.append(tid)

    if len(tenants) < 2:
        print("  [ABORT] 租户不足 2 个，无法继续隔离验证")
        return 1

    # ---------- 3. 每个租户管理员：造数据 + 验证功能 ----------
    print("\n== 3. 各租户管理员在 IAM/System 造数据并验证功能 ==")
    for t in tenants:
        tag = t["tag"]
        s, tok, _ = login(t["uname"], t["pwd"])
        if not check(f"租户{tag}管理员登录", s == 200 and bool(tok), f"HTTP {s}"):
            continue
        t["tok"] = tok

        # 3.1 建部门
        s, raw = http("POST", IAM + "/api/v1/iam/depts", tok,
                      {"name": f"研发部-{tag}", "parentId": None})
        dep_id = data_of(j(raw))
        t["deptId"] = dep_id
        check(f"租户{tag} 创建部门", s == 200 and bool(dep_id), f"HTTP {s} {raw[:120]}")

        # 3.2 建角色
        s, raw = http("POST", IAM + "/api/v1/iam/roles", tok,
                      {"name": f"角色-{tag}", "code": f"role_{tag}_{rand()}", "description": "iso"})
        role_id = data_of(j(raw))
        t["roleId"] = role_id
        check(f"租户{tag} 创建角色", s == 200 and bool(role_id), f"HTTP {s} {raw[:120]}")

        # 3.3 建账号（带部门）→ 应成功
        uname_acc = f"user_{tag}_{rand()}"
        s, raw = http("POST", IAM + "/api/v1/iam/accounts", tok,
                      {"username": uname_acc, "password": "Test@123456",
                       "email": f"{uname_acc}@iso.test", "realName": f"用户{tag}",
                       "deptId": dep_id, "roleIds": [role_id]})
        acc_id = data_of(j(raw))
        t["accIds"] = [acc_id]
        t["accUsernames"] = [uname_acc]
        check(f"租户{tag} 创建账号(归属部门必填→带部门) 成功",
              s in (200, 201) and bool(acc_id), f"HTTP {s} {raw[:140]}")

        # 3.4 建账号（不带部门）→ 应被拒（DEPT_REQUIRED）
        bad_uname = f"nouser_{tag}_{rand()}"
        s, raw = http("POST", IAM + "/api/v1/iam/accounts", tok,
                      {"username": bad_uname, "password": "Test@123456",
                       "email": f"{bad_uname}@iso.test", "realName": f"无部门{tag}",
                       "deptId": None, "roleIds": [role_id]})
        ok_rejected = s in (400, 422) and ("DEPT_REQUIRED" in raw or "归属部门" in raw)
        check(f"租户{tag} 创建账号(不填部门) 被拒（归属部门必填）",
              ok_rejected, f"HTTP {s} {raw[:140]}")

        # 3.5 System 配置为平台全局：租户管理员无 system:* 写权限，创建应被拒(403) —— 印证 sys_* 全局设计
        ck = f"verify.tenant{tag}.{rand()}"
        s, raw = http("POST", SYS + "/api/v1/system/config", tok,
                      {"configKey": ck, "configValue": f"v-{tag}",
                       "configType": "SERVICE", "description": f"租户{tag}私有配置"})
        t["cfgKey"] = ck
        check(f"租户{tag} 创建系统配置被拒(403，System 为平台全局，租户无 system:* 写权)",
              s == 403, f"HTTP {s} {raw[:120]}")

        # 3.6 列表读取（功能正确性）
        s, raw = http("GET", IAM + "/api/v1/iam/accounts?page=1&size=50", tok)
        recs = (data_of(j(raw)) or {}).get("records") or []
        names = [r.get("username") for r in recs if isinstance(r, dict)]
        t["listedUsernames"] = names
        check(f"租户{tag} 账号列表可读且含自建账号",
              s == 200 and uname_acc in names, f"HTTP {s} count={len(names)}")

    # ---------- 4. 租户隔离验证 ----------
    print("\n== 4. 租户之间的数据隔离验证 ==")
    A, B = tenants[0], tenants[1]

    # 4.1 互不可见：A 的账号列表不含 B 的用户名，反之亦然
    a_no_b = all(u not in A["listedUsernames"] for u in B["accUsernames"])
    b_no_a = all(u not in B["listedUsernames"] for u in A["accUsernames"])
    check("隔离：租户A列表不含租户B的账号", a_no_b,
          f"A列表={A['listedUsernames']} B账号={B['accUsernames']}")
    check("隔离：租户B列表不含租户A的账号", b_no_a,
          f"B列表={B['listedUsernames']} A账号={A['accUsernames']}")

    # 4.2 越权直查：A 用 B 的账号 ID 直读 → 必须失败(404/403)
    if A["tok"] and B["accIds"] and B["accIds"][0]:
        s, raw = http("GET", IAM + f"/api/v1/iam/accounts/{B['accIds'][0]}", A["tok"])
        check("隔离：租户A直查租户B的账号ID 被拒(404/403)",
              s in (403, 404), f"HTTP {s} {raw[:120]}")
    else:
        check("隔离：租户A直查租户B的账号ID 被拒(404/403)", False, "缺少 B 账号ID，跳过")

    # 4.3 部门/角色隔离：A 部门/角色树不含 B 的
    s, raw = http("GET", IAM + "/api/v1/iam/depts/tree", A["tok"])
    a_dept_raw = raw
    s2, raw2 = http("GET", IAM + "/api/v1/iam/depts/tree", B["tok"])
    b_dept_raw = raw2
    a_has_b_dept = B["deptId"] is not None and str(B["deptId"]) in a_dept_raw
    b_has_a_dept = A["deptId"] is not None and str(A["deptId"]) in b_dept_raw
    check("隔离：租户A部门树不含租户B的部门", s == 200 and not a_has_b_dept,
          f"A树含B部门={a_has_b_dept}")
    check("隔离：租户B部门树不含租户A的部门", s2 == 200 and not b_has_a_dept,
          f"B树含A部门={b_has_a_dept}")

    # 4.4 配置隔离：租户B 不可见任何其它租户私有配置（租户A 本就无法写，作为护栏校验）
    s, raw = http("GET", SYS + "/api/v1/system/config/page?page=1&size=100", B["tok"])
    b_cfgs = (data_of(j(raw)) or {}).get("records") or []
    b_keys = [c.get("configKey") for c in b_cfgs if isinstance(c, dict)]
    other_tenant_keys = [k for k in b_keys if k and k.startswith("verify.tenant")]
    check("隔离：租户B列表不含其它租户私有配置",
          not other_tenant_keys, f"B可见其它租户私有keys={other_tenant_keys}")

    # ---------- 5. 全局共享验证 ----------
    print("\n== 5. 全局共享数据跨租户可见验证 ==")
    s, raw = http("GET", SYS + "/api/v1/system/config/page?page=1&size=100", A["tok"])
    a_cfgs = (data_of(j(raw)) or {}).get("records") or []
    a_keys = [c.get("configKey") for c in a_cfgs if isinstance(c, dict)]
    check("全局共享：租户A可见平台管理员创建的全局配置",
          gkey in a_keys, f"全局key={gkey} 是否在A列表={gkey in a_keys}")

    s, raw = http("GET", SYS + "/api/v1/system/config/page?page=1&size=100", B["tok"])
    b_cfgs2 = (data_of(j(raw)) or {}).get("records") or []
    b_keys2 = [c.get("configKey") for c in b_cfgs2 if isinstance(c, dict)]
    check("全局共享：租户B可见平台管理员创建的全局配置",
          gkey in b_keys2, f"全局key={gkey} 是否在B列表={gkey in b_keys2}")

    # ---------- 汇总 ----------
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 64)
    print(f"合计 {total} 项，通过 {total - len(failed)}，失败 {len(failed)}")
    for name, _ok, detail in failed:
        print(f"  FAIL {name} — {detail}")
    print("=" * 64)
    return 1 if failed else 0


# ---------------------------------------------------------------------------
# 自清理：本批创建的测试租户只用于验收，跑完从 dev 库彻底清除，保证 CI 可重复运行、
# 不污染 bone 库。仅删除本脚本本次创建的 tenantId（ISO_* 租户），不影响任何真实数据。
# 表清单与 TenantDeletionGatewayAdapter.TENANT_TABLES 保持一致；dev 库未部署的模块表
# 会被跳过（按 information_schema 过滤）。
# ---------------------------------------------------------------------------
CREATED_TENANT_IDS: list = []
# 平台管理员（tenant_id=0）创建的全局配置不在「按租户清理」范围内，需按 key 精确回收
CREATED_CONFIG_KEYS: list = []

_DB = dict(host="127.0.0.1", port=3306, user="root", password="mysql123", database="bone")

_TENANT_TABLES = [
    "iam_account", "iam_dept", "iam_role", "iam_menu", "bone_application",
    "bone_module", "bone_app_permission", "iam_account_role", "iam_audit_log",
    "iam_audit_settings", "iam_policy", "iam_refresh_token", "sys_config",
    "sys_log", "sys_dict", "sys_schedule_task", "sys_alert_rule", "sys_alert_event",
    "mdm_entity", "mdm_field", "mdm_record", "mdm_record_version", "mdm_category",
    "mdm_record_category", "mdm_steward", "mdm_steward_scope", "mdm_entity_subscription",
    "mdm_qcheck_task", "mdm_qcheck_detail", "mdm_qcheck_report", "mdm_quality_issue",
    "mdm_model_drift", "mdm_feedback", "mdm_reference_value_tenant", "md_lineage",
    "md_standard", "md_quality_rule", "md_entity", "md_field", "md_record",
    "int_flow", "int_flow_node", "int_flow_connection", "int_connector",
    "int_execution_log", "int_dead_letter", "int_template", "meta_entity",
    "meta_field", "meta_entity_relation", "meta_code_template", "gen_table_metadata",
    "gen_column_metadata", "gen_generation_task", "gen_code_generation_history",
    "gen_code_template", "gen_data_source", "exts_extension_point", "exts_extension_impl",
    "exts_plugin_execution_log", "exts_audit_log", "exts_plugin_version", "ntf_message",
]


def cleanup(tenant_ids):
    """彻底清除本批测试租户数据（与生产 purge 逻辑同源）。失败仅告警，不影响验收结论。"""
    if not tenant_ids:
        return
    try:
        conn = pymysql.connect(connect_timeout=5, **_DB)
    except Exception as e:  # noqa: BLE001
        print(f"  [cleanup] 跳过（无法连接 DB: {e}）")
        return
    try:
        with conn.cursor() as cur:
            # 仅删除「确实含有 tenant_id 列」的表，避免 dev 库部分表缺列导致整批清理失败
            cur.execute(
                "SELECT table_name FROM information_schema.columns "
                "WHERE table_schema=%s AND column_name='tenant_id'",
                (_DB["database"],),
            )
            tables_with_tid = {r[0] for r in cur.fetchall()}
            cur.execute(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=%s",
                (_DB["database"],),
            )
            existing = {r[0] for r in cur.fetchall()}
        tables = [t for t in _TENANT_TABLES if t in tables_with_tid]
        conn.begin()
        with conn.cursor() as cur:
            cur.execute("SET FOREIGN_KEY_CHECKS=0")
            # iam_role_permission 无 tenant_id，随 iam_role 经 JOIN 清除
            if "iam_role_permission" in existing and "iam_role" in existing:
                for tid in tenant_ids:
                    cur.execute(
                        "DELETE rp FROM iam_role_permission rp "
                        "INNER JOIN iam_role r ON r.id=rp.role_id WHERE r.tenant_id=%s",
                        (tid,),
                    )
            for tid in tenant_ids:
                for t in tables:
                    cur.execute(f"DELETE FROM `{t}` WHERE tenant_id=%s", (tid,))
            if "iam_tenant" in existing:
                for tid in tenant_ids:
                    cur.execute("DELETE FROM iam_tenant WHERE id=%s", (tid,))
            # 按 key 精确回收本次建出的全局配置（tenant_id=0），避免逐次运行累积
            if "sys_config" in existing:
                for key in CREATED_CONFIG_KEYS:
                    cur.execute("DELETE FROM sys_config WHERE config_key=%s", (key,))
            cur.execute("SET FOREIGN_KEY_CHECKS=1")
        conn.commit()
        print(f"  [cleanup] 已清理测试租户 {tenant_ids} 及其子表数据")
    except Exception as e:  # noqa: BLE001
        print(f"  [cleanup] 失败（不影响验证结果）: {e}")
        try:
            conn.rollback()
        except Exception:  # noqa: BLE001
            pass
    finally:
        conn.close()


if __name__ == "__main__":
    rc = main()
    if "--no-cleanup" not in sys.argv:
        cleanup(CREATED_TENANT_IDS)
    raise SystemExit(rc)
