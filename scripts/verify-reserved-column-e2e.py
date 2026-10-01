#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""机制 B（预留列池）端到端验收：对 bone-blueprint 的 bp_payment 实体扩展字段，
验证「发布不 ALTER、ext_* 动态分配、field_metadata/column_allocation 落库、运行时读写经 ext_*」。

目标实体：bp_payment（tenant=0 平台，已 PUBLISHED+RUNTIME，物理表已含 63 个 ext_* 预留列池）。
验证点：
  1. 新增两个机制 B 字段（e2e_rk_*=VARCHAR、e2e_mm_*=TEXT），发布后其 physical_column 被回填进 ext_str_*/ext_text_* 池；
  2. field_metadata / column_allocation 各新增 2 条分配记录（按池符号判定，不强求绝对槽位，支持可重复运行）；
  3. 发布**不触发**运行时 ALTER（bp_payment 列数前后不变 = 85，ext 池 63 个完整）；
  4. 运行时 CRUD：经 ext_str_*/ext_text_* 写入，读取回逻辑名值正确；
  5. 清理后 bp_payment 回到改造前状态（12 活动字段、0 分配、85 列）。

幂等：每次运行使用随机字段码（e2e_rk_<rand> / e2e_mm_<rand>），不依赖删除接口；清理走 DB 直删 e2e_% 产物。
依赖：pymysql（仅物理结构 / 分配落库校验；缺失时自动降级跳过 SQL 校验）。
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:9001"
TENANT = "0"
ENTITY_ID = 760313249877983232
ENTITY_CODE = "bp_payment"
TABLE = "bp_payment"
HDR = {"X-Tenant-Id": TENANT}

RESULTS: list[tuple[str, bool, str]] = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print(f"  [{'PASS' if ok else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def http(method, path, body=None, headers=None, timeout=30):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method.upper())
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    for k, v in (headers or HDR).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def jget(raw):
    try:
        return json.loads(raw)
    except Exception:  # noqa: BLE001
        return None


def data_of(parsed):
    return parsed.get("data") if isinstance(parsed, dict) else None


# --------------------------------------------------------------------------
# DB 辅助（只读 + 清理写）
# --------------------------------------------------------------------------
class Db:
    def __init__(self):
        self.conn = None
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
        except Exception as e:  # noqa: BLE001
            print(f"  [WARN] DB 直连不可用，降级跳过 SQL 校验：{e}")

    def col_count(self, table):
        if not self.conn:
            return None
        with self.conn.cursor() as c:
            c.execute(
                "SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema='bone' AND table_name=%s", (table,))
            return c.fetchone()[0]

    def ext_cols(self, table):
        if not self.conn:
            return None
        with self.conn.cursor() as c:
            c.execute(
                "SELECT COLUMN_NAME FROM information_schema.columns "
                "WHERE table_schema='bone' AND table_name=%s AND COLUMN_NAME LIKE 'ext_%%'", (table,))
            return [r[0] for r in c.fetchall()]

    def count(self, sql, args=()):
        if not self.conn:
            return None
        with self.conn.cursor() as c:
            c.execute(sql, args)
            return c.fetchone()[0]

    def rows(self, sql, args=()):
        if not self.conn:
            return None
        with self.conn.cursor() as c:
            c.execute(sql, args)
            return c.fetchall()

    def execute(self, sql, args=()):
        if not self.conn:
            return None
        with self.conn.cursor() as c:
            return c.execute(sql, args)


def cleanup_db(db, codes, rec_id):
    """清理 e2e 测试产物（仅 e2e_% 命名空间），保证可重复运行。
    注意：column_allocation 表**没有 name 列**，分配行通过 field_metadata.name 反查 column_name 后按列名删除。
    """
    if not db.conn:
        return
    # 1) 反查这些机制 B 字段分配到的物理列名
    names_ph = ",".join(["%s"] * len(codes))
    cnames = db.rows(
        f"SELECT DISTINCT column_name FROM field_metadata "
        f"WHERE biz_identity_code=%s AND name IN ({names_ph})",
        (ENTITY_CODE, *codes))
    cname_list = [r[0] for r in (cnames or []) if r[0]]
    # 2) 删除分配落库（column_allocation 按 column_name；field_metadata 按 name）
    if cname_list:
        ph = ",".join(["%s"] * len(cname_list))
        db.execute(
            f"DELETE FROM column_allocation WHERE biz_identity_code=%s AND column_name IN ({ph})",
            (ENTITY_CODE, *cname_list))
    db.execute(
        f"DELETE FROM field_metadata WHERE biz_identity_code=%s AND name IN ({names_ph})",
        (ENTITY_CODE, *codes))
    # 3) 删除建模字段（meta_field）+ 运行时测试记录
    db.execute(
        "DELETE FROM meta_field WHERE entity_id=%s AND code LIKE 'e2e_%%'", (ENTITY_ID,))
    if rec_id is not None:
        db.execute(f"DELETE FROM {TABLE} WHERE id=%s", (rec_id,))
    db.execute(f"DELETE FROM {TABLE} WHERE order_id LIKE 'E2E%%' OR order_id >= 9000000000")


def main():
    ap = argparse.ArgumentParser(description="机制 B 预留列池 e2e 验收")
    ap.add_argument("--no-cleanup", action="store_true", help="保留测试产物（meta_field/分配/记录）")
    args = ap.parse_args()

    db = Db()
    # 运行前先清理历史 e2e 残留（含上次遗留的逻辑删除码 / 分配行）
    cleanup_db(db, ("e2e_rk_x", "e2e_mm_x"), None)  # 历史残留走 LIKE 'e2e_%'，codes 仅占位
    print(f"== 机制 B 预留列池 e2e（entity={ENTITY_CODE} tenant={TENANT}）==")

    # 随机码，保证可重复运行、无碰撞
    suf = uuid.uuid4().hex[:10]
    RK = f"e2e_rk_{suf}"
    MM = f"e2e_mm_{suf}"
    codes = [RK, MM]

    # 1. 实体前置校验
    st, raw = http("GET", f"/api/v1/metadata/entities/{ENTITY_ID}")
    ent = data_of(jget(raw)) or {}
    check("实体可见且为 PUBLISHED+RUNTIME",
          st == 200 and ent.get("status") == 1 and ent.get("deliveryMode") == 1,
          f"status={ent.get('status')} mode={ent.get('deliveryMode')} HTTP {st}")

    # 2. 基线快照
    base_cols = db.col_count(TABLE)
    base_alloc = db.count("SELECT COUNT(*) FROM column_allocation WHERE biz_identity_code=%s", (ENTITY_CODE,))
    base_fm = db.count("SELECT COUNT(*) FROM field_metadata WHERE biz_identity_code=%s", (ENTITY_CODE,))
    base_fields = db.count("SELECT COUNT(*) FROM meta_field WHERE entity_id=%s AND deleted=0", (ENTITY_ID,))
    print(f"  基线：列数={base_cols} 分配={base_alloc} field_metadata={base_fm} 活动字段={base_fields}")

    # 3. 新增两个机制 B 字段（前端建模路径）
    new_fields = [
        (RK, "E2E备注", "VARCHAR", 255),
        (MM, "E2E备忘", "TEXT", None),
    ]
    created_ids = {}
    for code, disp, ftype, flen in new_fields:
        payload = {
            "name": code, "code": code, "displayName": disp, "type": ftype,
            "length": flen, "required": False, "sortOrder": 0,
        }
        st, raw = http("POST", f"/api/v1/metadata/entities/{ENTITY_ID}/fields", body=payload)
        pid = data_of(jget(raw))
        ok = st in (200, 201) and pid
        check(f"新增机制 B 字段 {code}（{ftype}）", ok, f"HTTP {st} id={pid} {raw[:160]}")
        if pid:
            created_ids[code] = pid

    # 4. 重新发布（幂等）
    st, raw = http("POST", f"/api/v1/metadata/entities/{ENTITY_ID}/publish", body=None)
    check("重新发布实体（幂等，不 409）", st == 200, f"HTTP {st} {raw[:160]}")

    # 5. 验收：physical_column 回填进预留池
    phys_map = {}
    if db.conn:
        rows = db.rows(
            "SELECT code, physical_column FROM meta_field WHERE entity_id=%s AND code IN (%s,%s)",
            (ENTITY_ID, RK, MM))
        phys_map = {r[0]: r[1] for r in rows}
        pc_rk = phys_map.get(RK)
        pc_mm = phys_map.get(MM)
        check("e2e_rk 物理列回填进 ext_str_* 池", bool(pc_rk) and re.fullmatch(r"ext_str_\d+", pc_rk or ""),
              f"={pc_rk}")
        check("e2e_mm 物理列回填进 ext_text_* 池", bool(pc_mm) and re.fullmatch(r"ext_text_\d+", pc_mm or ""),
              f"={pc_mm}")
    else:
        print("  [SKIP] physical_column 回填校验（无 DB 直连）")

    # 6. 验收：field_metadata / column_allocation 落库
    if db.conn:
        alloc = db.count("SELECT COUNT(*) FROM column_allocation WHERE biz_identity_code=%s", (ENTITY_CODE,))
        fm = db.count("SELECT COUNT(*) FROM field_metadata WHERE biz_identity_code=%s", (ENTITY_CODE,))
        check("column_allocation 新增 2 条", alloc == (base_alloc or 0) + 2, f"count={alloc} (基线={base_alloc})")
        check("field_metadata 新增 2 条", fm == (base_fm or 0) + 2, f"count={fm} (基线={base_fm})")
        alloc_cols = db.rows(
            "SELECT data_type, column_name FROM column_allocation "
            "WHERE biz_identity_code=%s ORDER BY data_type", (ENTITY_CODE,))
        alloc_str = ", ".join(f"{t}={c}" for t, c in alloc_cols)
        check("分配列名落在预留池（STRING=ext_str_* / TEXT=ext_text_*）",
              any(re.fullmatch(r"ext_str_\d+", c or "") for _, c in alloc_cols)
              and any(re.fullmatch(r"ext_text_\d+", c or "") for _, c in alloc_cols),
              alloc_str)
    else:
        print("  [SKIP] 分配落库校验（无 DB 直连）")

    # 7. 验收：无运行时 ALTER（列数不变）
    after_cols = db.col_count(TABLE)
    if base_cols is None or after_cols is None:
        print("  [SKIP] 列数对比校验（无 DB 直连）")
    else:
        check("发布后物理表列数不变（无 ALTER）", after_cols == base_cols,
              f"before={base_cols} after={after_cols}")
        after_ext = db.ext_cols(TABLE)
        check("ext_* 预留池 63 个保持完整", after_ext is not None and len(after_ext) == 63,
              f"ext count={len(after_ext) if after_ext else 'n/a'}")

    # 8. 运行时 CRUD 经 ext_* 读写
    print("\n== 运行时 CRUD（经 ext_* 读写）==")
    st, raw = http("GET", f"/api/v1/metadata/entities/{ENTITY_ID}/fields?page=1&size=50")
    flist = (data_of(jget(raw)) or {}).get("records") or []
    payload = {}
    for f in flist:
        code = f.get("code")
        ftype = (f.get("type") or "").upper()
        if code in ("id", "tenant_id", "deleted", "version", "created_at", "updated_at", "created_by", "updated_by"):
            continue
        if ftype in ("LONG", "BIGINT", "INT", "INTEGER"):
            payload[code] = 1000000 + len(payload)
        elif ftype in ("DECIMAL", "NUMBER", "DOUBLE", "FLOAT"):
            payload[code] = 9.99
        elif ftype in ("DATE", "DATETIME", "TIMESTAMP", "TIME"):
            payload[code] = "2026-01-01 00:00:00"
        elif ftype in ("BOOLEAN", "BOOL"):
            payload[code] = False
        else:  # STRING / VARCHAR / TEXT / JSON / EMAIL / URL
            # 短值：真实列最短 VARCHAR 为 16（settle_status），9 字符内必不溢出
            payload[code] = f"v{suf[:8]}"
    # order_id 是 LONG 业务字段，必须是数值且唯一
    payload["order_id"] = 9000000000 + random.randint(0, 999999999)

    ide = str(uuid.uuid4())
    st, raw = http("POST", f"/api/v1/runtime/entities/{ENTITY_CODE}/records",
                   body=payload, headers={**HDR, "Idempotency-Key": ide})
    created = data_of(jget(raw)) or {}
    rec_id = created.get("id")
    check("运行时新增记录（含机制 B 字段）", st in (200, 201) and bool(rec_id),
          f"HTTP {st} id={rec_id} {raw[:160]}")
    if not rec_id:
        rec_id = None

    if rec_id:
        st, raw = http("GET", f"/api/v1/runtime/entities/{ENTITY_CODE}/records/{rec_id}")
        row = data_of(jget(raw)) or {}
        check("逻辑名 e2e_rk 读回正确", row.get(RK) == payload.get(RK), f"={row.get(RK)}")
        check("逻辑名 e2e_mm 读回正确", row.get(MM) == payload.get(MM), f"={row.get(MM)}")

        # 物理层确认：值确实落在 ext_str_*/ext_text_* 列
        if db.conn:
            pc_rk = phys_map.get(RK)
            pc_mm = phys_map.get(MM)
            phys = db.rows(
                f"SELECT `{pc_rk}`, `{pc_mm}` FROM {TABLE} WHERE id=%s", (rec_id,))
            if phys and pc_rk and pc_mm:
                v1, v2 = phys[0][0], phys[0][1]
                check(f"物理列 {pc_rk} 承载 e2e_rk 值", v1 == payload.get(RK), f"={v1}")
                check(f"物理列 {pc_mm} 承载 e2e_mm 值", v2 == payload.get(MM), f"={v2}")

        # 更新机制 B 字段
        new_rk = f"e2e_updated_{suf}"
        st, raw = http("PUT", f"/api/v1/runtime/entities/{ENTITY_CODE}/records/{rec_id}",
                       body={RK: new_rk}, headers={**HDR, "Idempotency-Key": str(uuid.uuid4())})
        check("更新机制 B 字段", st == 200, f"HTTP {st} {raw[:120]}")
        st, raw = http("GET", f"/api/v1/runtime/entities/{ENTITY_CODE}/records/{rec_id}")
        row = data_of(jget(raw)) or {}
        check("更新后逻辑名 e2e_rk 生效", row.get(RK) == new_rk, f"={row.get(RK)}")

        # 删除测试记录
        st, _ = http("DELETE", f"/api/v1/runtime/entities/{ENTITY_CODE}/records/{rec_id}")
        check("删除测试记录", st == 200, f"HTTP {st}")

    # 9. 清理（恢复 bp_payment 改造前）
    if not args.no_cleanup:
        print("\n== 清理测试产物（恢复 bp_payment 改造前状态）==")
        # 建模字段经 API 尽力删除（忽略 HTTP 0 等异常），DB 直删兜底
        for code, fid in created_ids.items():
            if fid:
                http("DELETE", f"/api/v1/metadata/entities/{ENTITY_ID}/fields/{fid}")
        cleanup_db(db, codes, rec_id)
        print("  [DB] 已清理 meta_field / field_metadata / column_allocation / 测试记录")
        fin_fields = db.count("SELECT COUNT(*) FROM meta_field WHERE entity_id=%s AND deleted=0", (ENTITY_ID,))
        fin_alloc = db.count("SELECT COUNT(*) FROM column_allocation WHERE biz_identity_code=%s", (ENTITY_CODE,))
        fin_fm = db.count("SELECT COUNT(*) FROM field_metadata WHERE biz_identity_code=%s", (ENTITY_CODE,))
        fin_cols = db.col_count(TABLE)
        check("清理后活动字段回到基线", fin_fields == base_fields, f"={fin_fields}/{base_fields}")
        check("清理后 column_allocation 回到 0", fin_alloc == 0, f"={fin_alloc}")
        check("清理后 field_metadata 回到 0", fin_fm == 0, f"={fin_fm}")
        check("清理后物理表列数不变", fin_cols == base_cols, f"={fin_cols}/{base_cols}")
    else:
        print("\n[--no-cleanup] 保留测试产物，未清理。")

    # 汇总
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("\n" + "=" * 64)
    print(f"机制 B e2e 合计 {total} 项，通过 {total - len(failed)}，失败 {len(failed)}")
    for n, _ok, d in failed:
        print(f"  FAIL {n} — {d}")
    print("=" * 64)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
