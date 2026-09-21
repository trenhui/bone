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


# 模块级路径常量，如 `const MD = '/api/v1/masterdata'`：不解析它，所有以 ${MD} 开头的路径都会被跳过
CONST_RE = re.compile(
    r"^\s*(?:const|let|var)\s+([A-Za-z_]\w*)\s*=\s*['\"]([^'\"]*)['\"]", re.MULTILINE
)
PARAM_RE = re.compile(r"\$\{[^}]*\}")
# 冒号动作路径：`/points/${id}:${action}`。动作名是动词（enable / deploy / ...）而不是 id，
# 若与其它参数一样替换成占位 `1`，会造出 `/points/1:1` 这种后端根本不存在的路径，
# 从而把「真实一致」误报成「方法不匹配」（2026-09-20：extension 两条告警均属此类假阳性）。
COLON_PARAM_RE = re.compile(r":\$\{[^}]*\}")

ACTION_MARKER = "::ACTION::"
# 探测时逐个替换的候选动作名：命中任一（200 且方法被 Allow 接受）即视为契约一致。
ACTION_VERBS = [
    "enable", "disable", "publish", "unpublish", "deploy", "undeploy",
    "rollback", "bind", "unbind", "simulate", "ingest", "install",
    "activate", "deactivate", "approve", "reject", "retry", "cancel",
    "sync", "execute", "preview", "validate", "archive", "restore",
    "reset", "refresh", "start", "stop", "pause", "resume", "submit", "verify",
]


def resolve_path(path: str, consts: dict):
    """把模板路径解析成可探测的字面路径。

    - 已知常量前缀（如 ${MD}）替换为常量值；
    - `:${action}` 形式的动作名替换为 ACTION_MARKER（由 run_contract 按 ACTION_VERBS 展开探测）；
    - 其余 ${...} 视为路径参数，替换为探针占位 `1`（OPTIONS 按 pattern 匹配，不绑定实参，
      因此占位值不影响「端点是否存在」的判定）。
    """
    for name, val in consts.items():
        path = path.replace("${" + name + "}", val)
    path = COLON_PARAM_RE.sub(":" + ACTION_MARKER, path)
    return PARAM_RE.sub("1", path)


def collect_frontend_paths(frontend_root: str):
    """返回 [(app, base, method, path)]。

    不做「含 ${ 就跳过」——那会让所有带路径参数的接口（`/entities/${id}/fields`）和所有用常量前缀的
    应用（masterdata 的 ${MD}）整批逃逸，使「全部命中」变成假绿。
    """
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
                consts = dict(CONST_RE.findall(text))
                for var, cbase in clients:
                    for method, path in build_call_re(var).findall(text):
                        out.append((app, cbase, method.lower(), resolve_path(path, consts)))
    # 去重（解析后可能出现重复，例如同一接口在多个文件里声明）
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

    # 覆盖度自检：某 app 一条都没扫到，多半是扫描器没识别其写法（而非它没有接口）——
    # 静默跳过会让「全部命中」变成假绿，必须与显式告警区分开。
    apps_with_src = [
        d
        for d in sorted(os.listdir(frontend_root))
        if os.path.isdir(os.path.join(frontend_root, d, "src"))
    ]
    hit_apps = {p[0] for p in paths}
    uncovered = [a for a in apps_with_src if a not in hit_apps]
    print(
        "  [覆盖] "
        + ", ".join(f"{a}={sum(1 for p in paths if p[0] == a)}" for a in hit_apps)
        + (f" | 零覆盖：{', '.join(uncovered)}" if uncovered else "")
    )
    if uncovered:
        check("每个微应用都纳入契约对账", False, f"零覆盖：{', '.join(uncovered)}")
    else:
        check("每个微应用都纳入契约对账", True, f"{len(hit_apps)}/{len(apps_with_src)} 个应用")

    missing, mismatched, inconclusive = [], [], []
    for app, fbase, method, path in paths:
        label = f"{app} {method.upper()} {fbase}{path}"
        if ACTION_MARKER in path:
            # 动作名未定：按候选动词展开，命中任一即视为后端实现了该动作
            hit, not_found, other = [], 0, []
            for verb in ACTION_VERBS:
                status, allow = probe_options(
                    base + fbase + path.replace(ACTION_MARKER, verb), token, tenant
                )
                if status == 200 and (not allow or method.upper() in allow.upper()):
                    hit.append(verb)
                elif status == 404:
                    not_found += 1
                else:
                    other.append(f"{verb}→HTTP {status}")
            if hit:
                continue
            if not_found == len(ACTION_VERBS):
                missing.append(label + "（所有候选动作均 404）")
            else:
                inconclusive.append(
                    f"{label} 动作名未定 → " + ", ".join(other[:3])
                )
            continue

        url = base + fbase + path
        status, allow = probe_options(url, token, tenant)
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


def pick_first_id(base: str, token: str, tenant: str, list_path: str):
    """从列表接口取第一个资源的 id，用于填充前端路径里的 ${id}。"""
    st, raw = http("GET", base + list_path, token=token,
                   headers={"X-Tenant-Id": str(tenant)}, timeout=15)
    data = data_of(json_of(raw)) or {}
    if isinstance(data, dict):
        records = data.get("records") or data.get("list") or []
        if records and isinstance(records[0], dict):
            return records[0].get("id")
    return None


def step_ui_read_paths(base: str, token: str, tenant: str, ent: dict):
    """Part C：前端页面打开时**真实会调**的读接口（含路径参数），用真实 id/code 跑一遍。

    Part A 只能探测不带模板变量的路径（`/entities`、`/accounts` 之类），而页面真正打开时
    调用的多是 `/entities/{id}`、`/roles/{id}/permissions` 这类带 id 的路径 —— 这些路径的
    404 / 500 只有用真实 id 调才发现得了，因此单列一段。
    """
    print("\n== Part C 前端页面读链路（真实 id 填充，只读）==")
    ids = {
        "account": pick_first_id(base, token, tenant, "/api/v1/iam/accounts?page=1&size=1"),
        "role": pick_first_id(base, token, tenant, "/api/v1/iam/roles?page=1&size=1"),
        "permission": pick_first_id(base, token, tenant, "/api/v1/iam/permissions?page=1&size=1"),
        "config": pick_first_id(base, token, tenant, "/api/v1/system/config/page?page=1&size=1"),
        "alertRule": pick_first_id(base, token, tenant, "/api/v1/system/alert/rules/page?page=1&size=1"),
        "app": pick_first_id(base, token, tenant, "/api/v1/apps?page=1&size=1"),
    }
    print("  真实 id：" + ", ".join(f"{k}={v}" for k, v in ids.items()))

    # (名称, 前端页面, 路径构造器)
    cases = [
        ("实体详情", "元数据 / 实体管理", lambda: f"/api/v1/metadata/entities/{ent['id']}"),
        ("实体字段列表", "元数据 / 字段管理",
         lambda: f"/api/v1/metadata/entities/{ent['id']}/fields?page=1&size=20"),
        ("关系列表", "元数据 / 关系管理", lambda: "/api/v1/metadata/relationships?page=1&size=20"),
        ("运行时记录列表", "元数据 / 运行时数据管理",
         lambda: f"/api/v1/runtime/entities/{ent['code']}/records?page=1&size=20"),
        ("账号详情", "IAM / 账号管理", lambda: f"/api/v1/iam/accounts/{ids['account']}"),
        ("角色详情", "IAM / 角色管理", lambda: f"/api/v1/iam/roles/{ids['role']}"),
        ("角色权限", "IAM / 角色管理", lambda: f"/api/v1/iam/roles/{ids['role']}/permissions"),
        ("权限详情", "IAM / 权限管理", lambda: f"/api/v1/iam/permissions/{ids['permission']}"),
        ("审计设置", "IAM / 审计设置", lambda: "/api/v1/iam/audit/settings"),
        ("配置详情", "系统 / 配置管理", lambda: f"/api/v1/system/config/{ids['config']}"),
        ("配置历史", "系统 / 配置管理", lambda: f"/api/v1/system/config/{ids['config']}/history"),
        ("告警规则详情", "系统 / 监控告警", lambda: f"/api/v1/system/alert/rules/{ids['alertRule']}"),
        ("模块列表", "元数据 / 应用模块", lambda: f"/api/v1/apps/{ids['app']}/modules"),
        ("控制台服务", "控制台", lambda: "/api/v1/console/services"),
        ("控制台资源", "控制台", lambda: "/api/v1/console/resources"),
        ("控制台指标", "控制台", lambda: "/api/v1/console/metrics"),
    ]

    for name, page, builder in cases:
        try:
            path = builder()
        except Exception as e:  # noqa: BLE001
            check(f"{name}（{page}）", False, f"路径构造失败：{e}")
            continue
        if "/None" in path or path.rstrip("/").endswith("None"):
            print(f"  [SKIP] {name}（{page}）：上游列表为空，取不到真实 id")
            continue
        st, raw = http("GET", base + path, token=token,
                       headers={"X-Tenant-Id": str(tenant)}, timeout=15)
        parsed = json_of(raw)
        ok = st == 200 and isinstance(parsed, dict) and parsed.get("success") is not False
        check(f"{name}（{page}）", ok, f"HTTP {st}" + ("" if ok else f" {raw[:140]}"))


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
    if ent:
        step_ui_read_paths(args.gateway, token, args.tenant, ent)

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
