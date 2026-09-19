#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MVP 全栈自动联调验收脚本（前后端契约对账 + 主链路功能验收）。

用法：
    python3 scripts/verify-mvp-e2e.py                 # 全量（默认经网关 8888）
    python3 scripts/verify-mvp-e2e.py --skip-contract  # 只跑主链路

依赖：python3 + pymysql（仅物理结构校验需要，缺失时自动跳过 SQL 校验）。

两部分：
  Part A 前后端 API 契约对账：扫描 bone-frontend 各微应用 services 层声明的请求路径，
         逐个探测后端是否真实存在（404 = 前端调了不存在的接口）。
  Part B MVP 验收主链路（doc/wiki/10-MVP-范围清单.md 第 117~127 行 7 步）：
         登录 → 建模 → 发布建表 → 运行时 CRUD → 加字段再发布加列不丢数据 → 租户隔离。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

DEFAULT_GATEWAY = "http://localhost:8888"
DEFAULT_IAM_DIRECT = "http://localhost:8081"
DEFAULT_METADATA_DIRECT = "http://localhost:9001"

RESULTS: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    RESULTS.append((name, bool(ok), detail))
    flag = "PASS" if ok else "FAIL"
    print(f"  [{flag}] {name}" + (f" — {detail}" if detail else ""))
    return bool(ok)


def http(
    method: str,
    url: str,
    token: str | None = None,
    body=None,
    headers: dict | None = None,
    timeout: int = 20,
):
    """返回 (status, raw_text)。非 2xx 也正常返回，不抛异常。"""
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
    if isinstance(parsed, dict):
        return parsed.get("data")
    return None


def jwt_claim(token: str, key: str):
    """解析 JWT payload（不验签，仅取 claim）。"""
    import base64

    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return json.loads(base64.urlsafe_b64decode(payload.encode())).get(key)
    except Exception:
        return None


# --------------------------------------------------------------------------
# Part A：前后端 API 契约对账
# --------------------------------------------------------------------------

# const <var> = createApiClient('<base>'[, ...]) —— 变量名各应用不统一（api / client / apiClient），
# 且 base 可以是空串（此时路径自带 /api/v1 前缀）。因此先建立「变量名 -> base」映射再按名匹配，
# 既避免误抓 console.log 之类，也不会漏掉 createApiClient('') 的应用。
CLIENT_RE = re.compile(r"const\s+(\w+)\s*=\s*createApiClient\(\s*['\"]([^'\"]*)['\"]")


def build_call_re(var: str):
    return re.compile(
        r"\b"
        + re.escape(var)
        # 泛型部分可能是嵌套类型（ApiResponse<PageResult<T>>），用 [^()]* 而非 [^>]* 匹配
        + r"\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^()]*>)?\s*\(\s*[`'\"](\/?[^`'\"]*)[`'\"]"
    )


def collect_frontend_paths(frontend_root: str):
    """返回 [(app, base, method, path)]，跳过含 ${} 模板变量与未声明 base 的路径。"""
    out = []
    if not os.path.isdir(frontend_root):
        return out
    for app in sorted(os.listdir(frontend_root)):
        app_dir = os.path.join(frontend_root, app)
        if not os.path.isdir(app_dir):
            continue
        src = os.path.join(app_dir, "src")
        if not os.path.isdir(src):
            continue
        for root, _dirs, files in os.walk(src):
            if "node_modules" in root:
                continue
            for fn in files:
                if not (fn.endswith(".ts") or fn.endswith(".tsx")):
                    continue
                if ".test." in fn or ".spec." in fn:
                    continue
                fp = os.path.join(root, fn)
                try:
                    text = open(fp, encoding="utf-8", errors="replace").read()
                except OSError:
                    continue
                clients = CLIENT_RE.findall(text)
                if not clients:
                    continue
                for var, cbase in clients:
                    for method, path in build_call_re(var).findall(text):
                        if "${" in path:
                            continue
                        out.append((app, cbase, method.lower(), path))
    # 去重
    seen, uniq = set(), []
    for item in out:
        if item not in seen:
            seen.add(item)
            uniq.append(item)
    return uniq


def probe_options(url: str, token: str, tenant: str):
    """用 OPTIONS 探测接口是否存在及支持的方法（不触发业务逻辑，避免写污染）。"""
    req = urllib.request.Request(url, method="OPTIONS")
    req.add_header("Authorization", "Bearer " + token)
    req.add_header("X-Tenant-Id", str(tenant))
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, resp.headers.get("Allow", "")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Allow", "") if e.headers else ""
    except Exception as e:
        return 0, str(e)


def run_contract(base: str, token: str, frontend_root: str, tenant: str):
    print("\n== Part A 前后端 API 契约对账（OPTIONS 探测，不产生写操作）==")
    paths = collect_frontend_paths(frontend_root)
    if not paths:
        check("扫描到前端 API 声明", False, f"未找到任何声明，检查 {frontend_root}")
        return
    check("扫描到前端 API 声明", True, f"{len(paths)} 条")

    missing, mismatched, inconclusive = [], [], []
    for app, fbase, method, path in paths:
        url = base + fbase + path
        status, allow = probe_options(url, token, tenant)
        label = f"{app} {method.upper()} {fbase}{path}"
        if status == 404:
            missing.append(label)
        elif status == 0:
            missing.append(label + " (连接失败)")
        elif status != 200:
            # 401/403/405/5xx 不能据此判定「后端存在」，否则鉴权或网关异常会伪装成契约一致
            inconclusive.append(f"{label} → HTTP {status}")
        elif allow and method.upper() not in allow.upper():
            mismatched.append(f"{label} → Allow={allow}")

    check(
        "前端声明的接口后端均存在",
        not missing,
        ("缺失：" + "; ".join(missing[:12])) if missing else "全部命中",
    )
    for item in mismatched[:10]:
        print(f"  [WARN] HTTP 方法不匹配：{item}")
    if inconclusive:
        print(f"  [WARN] 无法判定（非 200/404，不计为通过）{len(inconclusive)} 条：")
        for item in inconclusive[:10]:
            print(f"         - {item}")


# --------------------------------------------------------------------------
# Part B：MVP 主链路
# --------------------------------------------------------------------------


class Db:
    """只读直连用于物理结构校验；构造跨租户数据需要 UPDATE，必须显式开 --allow-db-write。"""

    def __init__(self, allow_write: bool = False):
        self.conn = None
        self.allow_write = allow_write
        self.host = os.getenv("BONE_DB_HOST", "127.0.0.1")
        self.database = os.getenv("BONE_DB_NAME", "bone")
        try:
            import pymysql  # noqa: WPS433

            self.conn = pymysql.connect(
                host=os.getenv("BONE_DB_HOST", "127.0.0.1"),
                port=int(os.getenv("BONE_DB_PORT", "3306")),
                user=os.getenv("BONE_DB_USER", "root"),
                password=os.getenv("BONE_DB_PASSWORD", "mysql123"),
                database=os.getenv("BONE_DB_NAME", "bone"),
                charset="utf8mb4",
                autocommit=True,
            )
        except Exception as e:  # 没有 pymysql 或连不上 → SQL 校验降级为跳过
            print(f"  [WARN] 数据库直连不可用，跳过物理结构校验：{e}")

    def table_exists(self, name: str):
        if not self.conn:
            return None
        with self.conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=%s AND table_name=%s",
                (os.getenv("BONE_DB_NAME", "bone"), name),
            )
            return cur.fetchone()[0] > 0

    def columns(self, name: str):
        if not self.conn:
            return None
        with self.conn.cursor() as cur:
            cur.execute(
                "SELECT column_name FROM information_schema.columns "
                "WHERE table_schema=%s AND table_name=%s ORDER BY ordinal_position",
                (os.getenv("BONE_DB_NAME", "bone"), name),
            )
            return [r[0] for r in cur.fetchall()]

    def count(self, name: str, where: str = "", args=()):
        if not self.conn:
            return None
        sql = f"SELECT COUNT(*) FROM `{name}`"
        if where:
            sql += " WHERE " + where
        with self.conn.cursor() as cur:
            cur.execute(sql, args)
            return cur.fetchone()[0]

    def execute(self, sql: str, args=()):
        """写原语自带闸门：避免后续新增写点绕过 --allow-db-write。"""
        if not self.conn:
            return None
        if not self.allow_write:
            raise RuntimeError("拒绝写入：未给 --allow-db-write")
        with self.conn.cursor() as cur:
            return cur.execute(sql, args)


def step_login(base: str, iam_direct: str, user: str, pwd: str):
    print("\n== B1 登录与 JWT ==")
    st, raw = http("POST", iam_direct + "/api/v1/iam/login", body={"username": user, "password": pwd})
    body = json_of(raw)
    token = (data_of(body) or {}).get("token") if body else None
    if not check("IAM 直连登录（:8081）", st == 200 and bool(token), f"HTTP {st}"):
        return None
    st2, raw2 = http("POST", base + "/api/v1/iam/login", body={"username": user, "password": pwd})
    body2 = json_of(raw2)
    token2 = (data_of(body2) or {}).get("token") if body2 else None
    check("网关登录（:8888，MVP-06）", st2 == 200 and bool(token2), f"HTTP {st2}")

    # 未认证必须 401（MVP-05）
    st3, _ = http("GET", base + "/api/v1/iam/accounts?page=1&size=1")
    check("未带 token 访问受保护资源 → 401", st3 == 401, f"HTTP {st3}")

    # token 刷新（MVP-05）：/refresh 与 /login 同为无 JWT 入口，是 ADR-0029 失败关闭的高危路径，
    # 曾因 rotate 未回传 tenantId 而恒 401 —— 这里锁死契约。
    refresh = (data_of(body) or {}).get("refreshToken")
    if refresh:
        # 网关对 /api/v1/iam/refresh 仍要求 Authorization（仅 login 在白名单），
        # 因此带上 access token 调用，与前端实际行为一致。
        st4, raw4 = http("POST", base + "/api/v1/iam/refresh", token=token,
                         body={"refreshToken": refresh})
        ok4 = st4 == 200 and bool((data_of(json_of(raw4)) or {}).get("accessToken"))
        check("刷新 token（/api/v1/iam/refresh）", ok4, f"HTTP {st4} {raw4[:120]}")
    else:
        print("  [SKIP] 登录响应未返回 refreshToken，跳过刷新校验")

    return token


def step_modeling(base: str, token: str, tenant: str, tenant_id: int, db: Db):
    print("\n== B2/B3 建模：实体 + 字段（MVP-01/03/08）==")
    suffix = str(int(time.time()))[-6:] + str(uuid.uuid4().hex[:4])
    code = "mvp_customer_" + suffix
    table = "meta_mvp_customer_" + suffix
    hdr = {"X-Tenant-Id": tenant}

    st, raw = http(
        "POST",
        base + "/api/v1/metadata/entities",
        token=token,
        headers=hdr,
        body={
            "name": "MVPCustomer",
            "code": code,
            "displayName": "MVP客户",
            "description": "MVP e2e",
            "tableName": table,
            "type": 0,
            "deliveryMode": 1,  # RUNTIME (MVP-02/03)
        },
    )
    entity_id = data_of(json_of(raw))
    if not check("创建实体（deliveryMode=RUNTIME）", st in (200, 201) and bool(entity_id), f"HTTP {st} {raw[:160]}"):
        return None

    st, raw = http("GET", base + f"/api/v1/metadata/entities/{entity_id}", token=token, headers=hdr)
    dto = data_of(json_of(raw)) or {}
    check("实体详情可读且 code 一致", st == 200 and dto.get("code") == code, f"HTTP {st}")

    fields = [
        ("name", "姓名", "VARCHAR", 64),
        ("phone", "手机号", "VARCHAR", 32),
        ("birthday", "生日", "DATE", None),
    ]
    ok_fields = True
    for fname, dname, ftype, flen in fields:
        payload = {
            "name": fname,
            "code": fname,
            "displayName": dname,
            "type": ftype,
            "length": flen,
            "required": fname == "name",
            "sortOrder": 0,
        }
        st, raw = http(
            "POST",
            base + f"/api/v1/metadata/entities/{entity_id}/fields",
            token=token,
            headers=hdr,
            body=payload,
        )
        fid = data_of(json_of(raw))
        if not (st in (200, 201) and fid):
            ok_fields = False
            print(f"      字段 {fname} 创建失败 HTTP {st} {raw[:200]}")
    check("创建 3 个字段（姓名/手机号/生日）", ok_fields)

    st, raw = http(
        "GET", base + f"/api/v1/metadata/entities/{entity_id}/fields?page=1&size=20",
        token=token, headers=hdr,
    )
    flist = (data_of(json_of(raw)) or {}).get("records") or []
    check("字段列表可查且数量 ≥3", st == 200 and len(flist) >= 3, f"HTTP {st} count={len(flist)}")
    return {"id": entity_id, "code": code, "table": table, "tenant": tenant, "tenant_id": tenant_id}


def step_publish(base: str, token: str, ent: dict, db: Db):
    print("\n== B4 发布并对齐物理结构（MVP-11）==")
    hdr = {"X-Tenant-Id": ent["tenant"]}
    st, raw = http(
        "POST", base + f"/api/v1/metadata/entities/{ent['id']}/publish",
        token=token, headers=hdr, body=None,
    )
    check("发布实体", st == 200, f"HTTP {st} {raw[:160]}")

    st, raw = http("GET", base + f"/api/v1/metadata/entities/{ent['id']}", token=token, headers=hdr)
    dto = data_of(json_of(raw)) or {}
    check("发布后状态=PUBLISHED", dto.get("statusLabel") == "PUBLISHED" or dto.get("status") == 1,
          f"status={dto.get('status')} label={dto.get('statusLabel')}")

    exists = db.table_exists(ent["table"])
    if exists is None:
        print("  [SKIP] 物理表存在性校验（无 DB 直连）")
    else:
        check("发布后物理表已创建", exists, ent["table"])
    return True


def step_runtime_crud(base: str, token: str, ent: dict, db: Db):
    print("\n== B5 运行时动态 CRUD（MVP-02/04）==")
    hdr = {"X-Tenant-Id": ent["tenant"]}
    base_url = f"{base}/api/v1/runtime/entities/{ent['code']}/records"

    st, raw = http("POST", base_url, token=token, headers=hdr,
                   body={"name": "张三", "phone": "13800000001", "birthday": "1990-01-01"})
    created = data_of(json_of(raw)) or {}
    rec_id = created.get("id")
    if not check("新增记录", st in (200, 201) and bool(rec_id), f"HTTP {st} {raw[:200]}"):
        return None

    st, raw = http("GET", base_url + f"/{rec_id}", token=token, headers=hdr)
    row = data_of(json_of(raw)) or {}
    check("按 ID 查询记录", st == 200 and row.get("name") == "张三", f"HTTP {st}")

    st, raw = http("GET", base_url + "?page=1&size=10", token=token, headers=hdr)
    page = data_of(json_of(raw)) or {}
    records = page.get("records") or page.get("list") or []
    check("分页列表查询", st == 200 and len(records) >= 1, f"HTTP {st} count={len(records)}")

    st, raw = http("PUT", base_url + f"/{rec_id}", token=token, headers=hdr, body={"name": "张三丰"})
    check("更新记录", st == 200, f"HTTP {st} {raw[:160]}")

    st, raw = http("GET", base_url + f"/{rec_id}", token=token, headers=hdr)
    row = data_of(json_of(raw)) or {}
    check("更新已生效", row.get("name") == "张三丰", f"name={row.get('name')}")

    # 第二条：留给 B6「加字段不丢数据」与 B7 隔离验证
    http("POST", base_url, token=token, headers=hdr,
         body={"name": "李四", "phone": "13800000002", "birthday": "1992-05-05"})

    # MVP 验收第 5 步含「删除」：另建一条并删掉，确认删除后查不到（不影响上面两条）
    st2, raw2 = http("POST", base_url, token=token, headers=hdr,
                     body={"name": "王五", "phone": "13800000003", "birthday": "1995-09-09"})
    second_id = (data_of(json_of(raw2)) or {}).get("id")
    if second_id:
        st3, _ = http("DELETE", base_url + f"/{second_id}", token=token, headers=hdr)
        check("删除记录", st3 == 200, f"HTTP {st3}")
        st4, _ = http("GET", base_url + f"/{second_id}", token=token, headers=hdr)
        check("删除后按 ID 查询 → 404", st4 == 404, f"HTTP {st4}")

    return {"id": rec_id}


def step_add_field_republish(base: str, token: str, ent: dict, rec: dict, db: Db):
    print("\n== B6 加字段 + 再次发布：加列且不丢数据（MVP-11）==")
    hdr = {"X-Tenant-Id": ent["tenant"]}
    cols_before = db.columns(ent["table"])
    rows_before = db.count(ent["table"], "tenant_id=%s", (ent["tenant_id"],))

    st, raw = http(
        "POST", base + f"/api/v1/metadata/entities/{ent['id']}/fields",
        token=token, headers=hdr,
        body={"name": "email", "code": "email", "displayName": "邮箱", "type": "VARCHAR",
              "length": 128, "required": False},
    )
    if not check("新增可选字段 email", st in (200, 201), f"HTTP {st} {raw[:160]}"):
        return

    st, raw = http("POST", base + f"/api/v1/metadata/entities/{ent['id']}/publish",
                   token=token, headers=hdr, body=None)
    check("已发布实体可再次发布（幂等，不 409）", st == 200, f"HTTP {st} {raw[:160]}")

    cols_after = db.columns(ent["table"])
    if cols_before is None:
        print("  [SKIP] 列数校验（无 DB 直连）")
    else:
        check("物理表已 ADD COLUMN", "email" in (cols_after or []),
              f"before={len(cols_before)} after={len(cols_after or [])}")

    rows_after = db.count(ent["table"], "tenant_id=%s", (ent["tenant_id"],))
    if rows_before is None:
        print("  [SKIP] 数据保全校验（无 DB 直连）")
    else:
        check("原数据未丢失", rows_after == rows_before and rows_after >= 2,
              f"before={rows_before} after={rows_after}")


def step_tenant_isolation(base: str, token: str, ent: dict, rec: dict, db: Db):
    """双层租户隔离（MVP-04 / ADR-0029）。

    租户来自 JWT claim（ADR-0029：不以 header / body 覆盖）。为在单账号环境下仍可回归，
    这里把**本次 e2e 自建**的实体与记录临时改挂到另一租户，验证当前 token 立刻读不到，
    再改回原租户（无副作用）。
    """
    print("\n== B7 双层租户隔离（MVP-04 / ADR-0029）==")
    other = ent["tenant_id"] + 100000
    runtime_url = f"{base}/api/v1/runtime/entities/{ent['code']}/records"

    if not db.conn:
        print("  [SKIP] 构造跨租户数据需要 DB 直连（当前不可用，记为跳过而非失败）")
        return
    if not db.allow_write:
        print("  [SKIP] 未给 --allow-db-write，跳过「改挂租户」构造（该步骤会 UPDATE 业务库）")
        return
    if db.host not in ("127.0.0.1", "localhost") or not db.database.startswith("bone"):
        check("租户隔离可回归", False,
              f"拒绝在 {db.host}/{db.database} 上做跨租户写入（仅允许本地 bone* 库）")
        return
    if not rec.get("id") or not ent.get("id"):
        check("租户隔离可回归", False, "缺少 record id / entity id，拒绝执行 UPDATE")
        return

    try:
        # ① 运行时记录：改挂其他租户 → 当前租户必须读不到（404 / 列表为空）
        db.execute(f"UPDATE `{ent['table']}` SET tenant_id=%s WHERE id=%s", (other, rec["id"]))
        st, raw = http("GET", runtime_url + f"/{rec['id']}", token=token,
                       headers={"X-Tenant-Id": str(ent["tenant"])})
        check("跨租户记录按 ID 直读 → 404（拒绝越权）", st == 404, f"HTTP {st} {raw[:120]}")

        st, raw = http("GET", runtime_url + "?page=1&size=20", token=token,
                       headers={"X-Tenant-Id": str(ent["tenant"])})
        page = data_of(json_of(raw)) or {}
        records = page.get("records") or page.get("list") or []
        ids = [str(r.get("id")) for r in records]
        check("跨租户记录不出现在当前租户列表", str(rec["id"]) not in ids,
              f"列表条数={len(records)}")

        # ② 建模 catalog：实体改挂其他租户 → 当前租户看不到
        db.execute("UPDATE meta_entity SET tenant_id=%s WHERE id=%s", (other, ent["id"]))
        st, raw = http("GET", base + "/api/v1/metadata/entities?page=1&size=200", token=token,
                       headers={"X-Tenant-Id": str(ent["tenant"])})
        page = data_of(json_of(raw)) or {}
        records = page.get("records") or page.get("list") or []
        codes = [r.get("code") for r in records]
        check("跨租户实体不出现在当前租户的实体列表", ent["code"] not in codes,
              f"列表条数={len(records)}")
    except Exception as e:  # noqa: BLE001
        # 构造阶段失败也只记 FAIL：不让它冒泡出去，否则 B8 冒烟与汇总都不会执行
        check("跨租户隔离构造", False, f"{type(e).__name__}: {e}")
    finally:
        # 还原失败要单独报 FAIL，而不是抛异常中断后续步骤（否则 B8 冒烟永远不会执行）
        for sql, args in (
            (f"UPDATE `{ent['table']}` SET tenant_id=%s WHERE id=%s",
             (ent["tenant_id"], rec["id"])),
            ("UPDATE meta_entity SET tenant_id=%s WHERE id=%s", (ent["tenant_id"], ent["id"])),
        ):
            try:
                affected = db.execute(sql, args)
                check("跨租户数据已还原", affected == 1, f"rowcount={affected} sql={sql[:60]}")
            except Exception as e:  # noqa: BLE001
                check("跨租户数据已还原", False, f"{type(e).__name__}: {e}")

    # 还原后必须重新可见（证明上面的不可见来自租户谓词，而非数据被破坏）
    st, raw = http("GET", runtime_url + f"/{rec['id']}", token=token,
                   headers={"X-Tenant-Id": str(ent["tenant"])})
    check("还原租户后记录重新可读", st == 200, f"HTTP {st}")


def step_module_smoke(base: str, token: str, tenant: str):
    print("\n== B8/B9 各模块核心读接口冒烟（MVP-05/07/09）==")
    hdr = {"X-Tenant-Id": tenant}
    cases = [
        ("IAM 账号列表", "GET", "/api/v1/iam/accounts?page=1&size=5"),
        ("IAM 角色列表", "GET", "/api/v1/iam/roles?page=1&size=5"),
        ("IAM 权限树", "GET", "/api/v1/iam/permissions/tree"),
        ("IAM 菜单树", "GET", "/api/v1/iam/menus/tree"),
        ("IAM 审计日志", "GET", "/api/v1/iam/audit/logs?page=1&size=5"),
        ("控制台 overview", "GET", "/api/v1/console/overview"),
        ("控制台 quick-actions", "GET", "/api/v1/console/quick-actions"),
        ("系统配置", "GET", "/api/v1/system/config/page?page=1&size=5"),
        ("系统日志", "GET", "/api/v1/system/logs/page?page=1&size=5"),
        ("告警规则", "GET", "/api/v1/system/alert/rules/page?page=1&size=5"),
        ("告警事件", "GET", "/api/v1/system/alert/events/page?page=1&size=5"),
        ("系统健康检查", "GET", "/api/v1/system/health"),
        ("元数据实体列表", "GET", "/api/v1/metadata/entities?page=1&size=5"),
    ]
    for name, method, path in cases:
        st, raw = http(method, base + path, token=token, headers=hdr, timeout=15)
        parsed = json_of(raw)
        ok = st == 200 and isinstance(parsed, dict) and parsed.get("success") is True
        check(name, ok, f"HTTP {st}" + ("" if ok else f" {raw[:120]}"))


def main() -> int:
    ap = argparse.ArgumentParser(description="MVP 全栈自动联调验收")
    ap.add_argument("--gateway", default=DEFAULT_GATEWAY)
    ap.add_argument("--iam", default=DEFAULT_IAM_DIRECT)
    ap.add_argument("--metadata", default=DEFAULT_METADATA_DIRECT)
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", default="123456")
    ap.add_argument("--tenant", default="1001")
    ap.add_argument("--frontend", default=os.path.join(os.path.dirname(__file__), "..", "bone-frontend", "apps"))
    ap.add_argument("--skip-contract", action="store_true")
    ap.add_argument(
        "--allow-db-write",
        action="store_true",
        help="允许在【本地 bone* 库】上临时改挂 tenant_id 以构造跨租户数据（B7 需要；默认关闭则该步骤 SKIP）",
    )
    args = ap.parse_args()

    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    frontend_root = os.path.abspath(args.frontend)

    print(f"MVP 自动联调：gateway={args.gateway} tenant={args.tenant}")

    token = step_login(args.gateway, args.iam, args.user, args.password)
    if not token:
        print("\n登录失败，后续步骤无法继续。")
        return 1

    if not args.skip_contract:
        run_contract(args.gateway, token, frontend_root, args.tenant)

    # 租户以 JWT claim 为准（ADR-0029：不允许 header/body 覆盖）；--tenant 仅作为兜底
    tenant_id = int(jwt_claim(token, "tenantId") or args.tenant)
    print(f"  JWT tenantId={tenant_id}（header 不覆盖租户，仅用于显式化请求上下文）")

    db = Db(allow_write=args.allow_db_write)
    ent = step_modeling(args.gateway, token, args.tenant, tenant_id, db)
    if not ent:
        return summarize()
    step_publish(args.gateway, token, ent, db)
    rec = step_runtime_crud(args.gateway, token, ent, db)
    if rec:
        step_add_field_republish(args.gateway, token, ent, rec, db)
        step_tenant_isolation(args.gateway, token, ent, rec, db)
    step_module_smoke(args.gateway, token, args.tenant)

    # 元数据直连（绕过网关）一致性
    st, raw = http("GET", args.metadata + "/api/v1/metadata/entities?page=1&size=1",
                   token=token, headers={"X-Tenant-Id": args.tenant})
    check("元数据服务直连（:9001）可用", st == 200, f"HTTP {st}")
    print(f"\n(root={root})")
    return summarize()


def summarize() -> int:
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 60)
    print(f"合计 {total} 项，通过 {total - len(failed)}，失败 {len(failed)}")
    for name, _ok, detail in failed:
        print(f"  FAIL {name} — {detail}")
    print("=" * 60)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
