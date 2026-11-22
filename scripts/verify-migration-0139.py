#!/usr/bin/env python3
"""在隔离库上实跑 0139，验证两件事：

A. 正常路径幂等：连跑两遍都不报错，结果收敛（2 条报案 / report_id 全非空 / uk 存在）
B. fail-closed 守卫真的会红，且**报的是可读原因**（这正是本轮改动要证明的东西）：
   sp_pre_0139  → 收紧 NOT NULL 前发现 NULL 行
   sp_verify_0139 → 无主赔案 / 悬空 report_id / 列仍可空 / 唯一键缺失 / 报案号丢 RP 前缀

不触碰共享活库 bone，只在 bone_v0139 上跑。
"""
import pathlib
import re
import sys

import pymysql

REPO = pathlib.Path(__file__).resolve().parent.parent
MIG = REPO / "scripts/migration/0139_synap_claim_report_case_split.sql"
HOST, USER, PWD = "127.0.0.1", "root", "mysql123"
DB = "bone_v0139"

PRE_SCHEMA = """
CREATE TABLE t_claim_case (
  id BIGINT NOT NULL,
  claim_no VARCHAR(64) NOT NULL,
  policy_no VARCHAR(32) NOT NULL,
  plan_id BIGINT NOT NULL,
  insured_id_hash CHAR(64) NOT NULL,
  claim_type VARCHAR(32) NOT NULL,
  out_insure_time DATETIME(3) NOT NULL,
  plan_version VARCHAR(32) NOT NULL DEFAULT '',
  source_channel VARCHAR(32) NOT NULL DEFAULT '',
  source_system VARCHAR(32) NOT NULL DEFAULT 'self',
  closed_at DATETIME(3) DEFAULT NULL,
  tenant_id BIGINT NOT NULL,
  created_by BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_by BIGINT DEFAULT NULL,
  updated_at DATETIME(3) NOT NULL,
  batch_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '批次号(报案号前缀+复制赔案分组)',
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_no (claim_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""


def split_statements(path):
    """忠实模拟 mysql 客户端的切分：支持 DELIMITER、跳过字符串内分号与 -- 行注释。"""
    text = pathlib.Path(path).read_text(encoding="utf-8")
    out, buf, i, n = [], [], 0, len(text)
    quote = None
    delim = ";"
    line_start = True
    while i < n:
        if quote is None and line_start:
            m = re.match(r"DELIMITER[ \t]+(\S+)", text[i:], re.IGNORECASE)
            if m:
                delim = m.group(1)
                j = text.find("\n", i)
                i = n if j == -1 else j
                line_start = True
                continue
        ch = text[i]
        if quote:
            buf.append(ch)
            if ch == quote:
                if i + 1 < n and text[i + 1] == quote:
                    buf.append(text[i + 1])
                    i += 2
                    line_start = False
                    continue
                quote = None
            i += 1
            line_start = False
        elif ch in ("'", '"', "`"):
            quote = ch
            buf.append(ch)
            i += 1
            line_start = False
        elif ch == "-" and i + 1 < n and text[i + 1] == "-":
            j = text.find("\n", i)
            i = n if j == -1 else j
        else:
            if text.startswith(delim, i):
                s = "".join(buf).strip()
                if s:
                    out.append(s)
                buf = []
                i += len(delim)
                line_start = False
            else:
                buf.append(ch)
                i += 1
                line_start = ch == "\n"
    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out


def conn(db=None):
    return pymysql.connect(host=HOST, user=USER, password=PWD, database=db,
                           charset="utf8mb4", autocommit=True)


def exec_all(sql_list, label=""):
    c = conn(DB)
    cur = c.cursor()
    for i, s in enumerate(sql_list, 1):
        try:
            cur.execute(s)
            while cur.nextset():
                pass
        except Exception as e:
            c.close()
            raise RuntimeError(f"{label} 第 {i} 条语句失败: {e}\n--- SQL 前 200 字 ---\n{s[:200]}")
    c.close()


def fresh(pre=True):
    c = conn()
    cur = c.cursor()
    cur.execute(f"DROP DATABASE IF EXISTS {DB}")
    cur.execute(f"CREATE DATABASE {DB} CHARACTER SET utf8mb4")
    c.close()
    if not pre:
        return
    c = conn(DB)
    cur = c.cursor()
    cur.execute(PRE_SCHEMA)
    for cid, suffix in ((101, "A" * 22), (102, "B" * 22)):
        cur.execute(
            "INSERT INTO t_claim_case (id, claim_no, policy_no, plan_id, insured_id_hash,"
            " claim_type, out_insure_time, plan_version, source_channel, source_system,"
            " closed_at, tenant_id, created_by, created_at, updated_by, updated_at,"
            " batch_no, deleted) VALUES (%s,%s,%s,%s,%s,%s,NOW(3),'v1','app','self',"
            "%s,1,9,NOW(3),9,NOW(3),'batch-1',0)",
            (cid, "CL" + suffix, "P" + str(cid), 7, "a" * 64, "OUTPATIENT",
             None if cid == 101 else "2026-11-01 00:00:00"),
        )
    c.close()


def proc_ddl(name):
    s = pathlib.Path(MIG).read_text(encoding="utf-8")
    m = re.search(r"CREATE PROCEDURE %s\(\)(.*?)\nEND\$\$" % name, s, re.S)
    if not m:
        raise RuntimeError(f"未能在迁移文件里找到 {name} 的过程体")
    return "CREATE PROCEDURE %s()%s\nEND" % (name, m.group(1))


def call_proc(name):
    """建过程 → 调用 → 返回 (是否抛错, 错误码, 错误消息)。"""
    c = conn(DB)
    cur = c.cursor()
    cur.execute(f"DROP PROCEDURE IF EXISTS {name}")
    cur.execute(proc_ddl(name))
    code, msg = 0, ""
    try:
        cur.execute(f"CALL {name}()")
        while cur.nextset():
            pass
    except pymysql.MySQLError as e:
        code = e.args[0] if e.args else 0
        msg = str(e)
    cur.execute(f"DROP PROCEDURE IF EXISTS {name}")
    c.close()
    return bool(code), code, msg


RESULTS = []


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    print(("  ✅ " if cond else "  ❌ ") + name + (f"  [{detail}]" if detail and not cond else ""))


def main():
    stmts = split_statements(MIG)
    print(f"0139 切分出 {len(stmts)} 条语句\n")

    print("== A. 正常路径：连跑两遍 ==")
    fresh()
    exec_all(stmts, "第1遍")
    exec_all(stmts, "第2遍")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("SELECT COUNT(*), SUM(report_id IS NULL) FROM t_claim_case")
    total, nulls = cur.fetchone()
    cur.execute("SELECT COUNT(*) FROM t_claim_report")
    reports = cur.fetchone()[0]
    cur.execute("SELECT is_nullable FROM information_schema.columns WHERE table_schema=%s"
                " AND table_name='t_claim_case' AND column_name='report_id'", (DB,))
    nullable = cur.fetchone()[0]
    cur.execute("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics"
                " WHERE table_schema=%s AND table_name='t_claim_report' AND index_name='uk_report_no'"
                " AND non_unique=0", (DB,))
    uk = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM t_claim_report WHERE report_no NOT LIKE 'RP%'")
    bad_prefix = cur.fetchone()[0]
    cur.execute("SELECT column_comment FROM information_schema.columns WHERE table_schema=%s"
                " AND table_name='t_claim_case' AND column_name='batch_no'", (DB,))
    batch_comment = cur.fetchone()[0]
    c.close()
    check("两遍均无异常（幂等）", True)
    check("报案行数=2（不重复插）", reports == 2, f"实际 {reports}")
    check("赔案行数=2 且无 NULL report_id", total == 2 and not nulls, f"total={total} null={nulls}")
    check("report_id 已收敛 NOT NULL", nullable == "NO", f"is_nullable={nullable}")
    check("uk_report_no 严格单列", uk == "report_no", f"实际 {uk}")
    check("报案号全部带 RP 前缀", bad_prefix == 0, f"违规 {bad_prefix} 行")
    check("§9 batch_no 注释已纠正", "不参与编号生成" in (batch_comment or ""), batch_comment or "")

    print("\n== B. fail-closed 守卫逐个触发 ==")

    # B1 sp_pre_0139：收紧前发现 NULL 行
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("ALTER TABLE t_claim_case MODIFY report_id BIGINT NULL")
    cur.execute("INSERT INTO t_claim_case (id, claim_no, policy_no, plan_id, insured_id_hash,"
                " claim_type, out_insure_time, tenant_id, created_at, updated_at, batch_no, deleted)"
                " VALUES (103,'CL" + "C" * 22 + "','P103',7,%s,'OUTPATIENT',NOW(3),1,NOW(3),NOW(3),'',0)",
                ("a" * 64,))
    c.close()
    raised, code, msg = call_proc("sp_pre_0139")
    check("B1 有 NULL 行时 sp_pre_0139 中止", raised and "still NULL" in msg and code == 1644, f"code={code} {msg}")

    # B2 无主赔案
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("ALTER TABLE t_claim_case MODIFY report_id BIGINT NULL")
    cur.execute("INSERT INTO t_claim_case (id, claim_no, policy_no, plan_id, insured_id_hash,"
                " claim_type, out_insure_time, tenant_id, created_at, updated_at, batch_no, deleted)"
                " VALUES (104,'CL" + "D" * 22 + "','P104',7,%s,'OUTPATIENT',NOW(3),1,NOW(3),NOW(3),'',0)",
                ("a" * 64,))
    c.close()
    raised, code, msg = call_proc("sp_verify_0139")
    check("B2 无主赔案被拦（10.1）", raised and "without report_id" in msg and code == 1644, f"code={code} {msg}")

    # B3 悬空 report_id
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("UPDATE t_claim_case SET report_id=999999 WHERE id=101")
    c.close()
    raised, code, msg = call_proc("sp_verify_0139")
    check("B3 悬空 report_id 被拦（10.2）", raised and "dangling" in msg and code == 1644, f"code={code} {msg}")

    # B4 列仍可空（无 NULL 行，只有可空性不对）
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("ALTER TABLE t_claim_case MODIFY report_id BIGINT NULL")
    c.close()
    raised, code, msg = call_proc("sp_verify_0139")
    check("B4 列仍可空被拦（10.3）", raised and "still nullable" in msg and code == 1644, f"code={code} {msg}")

    # B5 唯一键缺失
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("ALTER TABLE t_claim_report DROP INDEX uk_report_no")
    c.close()
    raised, code, msg = call_proc("sp_verify_0139")
    check("B5 唯一键缺失被拦（10.4）", raised and "uk_report_no missing" in msg and code == 1644, f"code={code} {msg}")

    # B6 报案号丢 RP 前缀
    fresh()
    exec_all(stmts, "准备")
    c = conn(DB)
    cur = c.cursor()
    cur.execute("UPDATE t_claim_report SET report_no='XX' WHERE id=101")
    c.close()
    raised, code, msg = call_proc("sp_verify_0139")
    check("B6 丢 RP 前缀被拦（10.6）", raised and "RP prefix" in msg and code == 1644, f"code={code} {msg}")
    txt = pathlib.Path(MIG).read_text(encoding="utf-8")
    body = re.search(r"CREATE PROCEDURE sp_verify_0139\(\)(.*?)\nEND\$\$", txt, re.S).group(1)
    i_sel = body.find("SELECT id, tenant_id, report_no FROM t_claim_report")
    i_sig = body.find("report_no without RP prefix")
    check("B6 诊断 SELECT 位于 SIGNAL 之前（结构）", 0 <= i_sel < i_sig, f"sel={i_sel} sig={i_sig}")

    c = conn(DB)
    cur = c.cursor()
    cur.execute("SELECT id, report_no FROM t_claim_report WHERE report_no NOT LIKE 'RP%'")
    diag = cur.fetchall()
    c.close()
    check("B6 诊断 SELECT 能取到违规行", len(diag) == 1 and diag[0][1] == "XX", str(diag))

    c = conn()
    cur = c.cursor()
    cur.execute(f"DROP DATABASE IF EXISTS {DB}")
    c.close()

    bad = [r for r in RESULTS if not r[1]]
    print(f"\n{'=' * 60}\n合计 {len(RESULTS)} 项，失败 {len(bad)} 项")
    for n, _, d in bad:
        print(f"  ❌ {n}: {d}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
