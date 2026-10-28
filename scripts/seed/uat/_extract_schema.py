#!/usr/bin/env python3
"""一次性脚本：合并 bone-init.sql 与各 migration 的 CREATE/ALTER，输出目标表最终列清单。

输出：scripts/seed/uat/.schema_reference.json
用法：python3 _extract_schema.py
"""
import re, json, os, glob

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
INIT = os.path.join(ROOT, "bone-init.sql")
MIG_DIR = os.path.join(ROOT, "scripts", "migrate")  # 可能是 migrate 或 migration
if not os.path.isdir(MIG_DIR):
    MIG_DIR = os.path.join(ROOT, "scripts", "migration")

def read_sql(path):
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()
    except Exception:
        return ""

# 收集所有文件，按"bone-init 优先，其余按文件名数字前缀排序"
files = [(INIT, 0)]
for p in sorted(glob.glob(os.path.join(MIG_DIR, "*.sql"))):
    base = os.path.basename(p)
    m = re.match(r"(\d+)", base)
    order = int(m.group(1)) if m else 9999
    files.append((p, order))
files.sort(key=lambda x: x[1])

sql_all = "\n".join(read_sql(p) for p, _ in files)

# 拆分为语句（按分号，容忍列内无分号）
stmts = re.split(r";\s*\n", sql_all)

creates = {}      # table -> column list (ordered)
create_full = {}  # table -> full create text
alters = {}       # table -> list of added column def strings

def parse_create(tbl, text):
    # 提取列定义：括号内第一行起，到 ) ENGINE 之前
    m = re.search(r"CREATE TABLE\s+`?" + re.escape(tbl) + r"`?\s*\((.*?)\)\s*ENGINE", text, re.S | re.I)
    if not m:
        m = re.search(r"CREATE TABLE\s+`?" + re.escape(tbl) + r"`?\s*\((.*?)\)\s*;", text, re.S | re.I)
    if not m:
        return None
    body = m.group(1)
    cols = []
    for line in body.split("\n"):
        line = line.strip().rstrip(",")
        if not line:
            continue
        # 列定义以反引号或字母开头，不以 KEY/CONSTRAINT/PRIMARY/UNIQUE/INDEX 开头
        if re.match(r"(PRIMARY KEY|UNIQUE KEY|KEY |CONSTRAINT|INDEX|FOREIGN KEY|UNIQUE \(|FULLTEXT)", line, re.I):
            continue
        cm = re.match(r"`?([a-zA-Z0-9_]+)`?\s+(.*)$", line)
        if cm:
            cols.append((cm.group(1), line))
    return cols

for s in stmts:
    s = s.strip()
    if not s:
        continue
    cm = re.match(r"CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", s, re.I)
    if cm:
        tbl = cm.group(1)
        cols = parse_create(tbl, s)
        if cols is not None:
            creates[tbl] = cols
            create_full[tbl] = s
        continue
    am = re.match(r"ALTER TABLE\s+`?([a-zA-Z0-9_]+)`?", s, re.I)
    if am:
        tbl = am.group(1)
        # 找所有 ADD [COLUMN] `col` ... （到下一个 ADD 或语句尾）
        for add in re.finditer(r"ADD\s+(?:COLUMN\s+)?`?([a-zA-Z0-9_]+)`?\s+([^,(][^;]*?)(?=,\s*ADD|;\s*$)", s, re.I):
            col = add.group(1)
            alters.setdefault(tbl, []).append((col, "ADD " + col + " " + add.group(2).strip()))
        continue

# 合并：create 列 + alter 新增列（去重，alter 优先）
result = {}
for tbl, cols in creates.items():
    seen = {c[0] for c in cols}
    merged = list(cols)
    for col, deftext in alters.get(tbl, []):
        if col not in seen:
            merged.append((col, deftext))
            seen.add(col)
    result[tbl] = [c[0] for c in merged]

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".schema_reference.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print("提取表数量:", len(result))
print("输出:", out)
# 打印目标表清单
target_prefixes = ("iam_","sys_","meta_","mdm_","md_","wf_","int_","gen_","bp_","t_order","t_claim","t_quota","t_adj","t_expense","settle_","ai_","exts_","cnsl_","ntf_","bone_","claim_","appeal_","hitl_","eval_","rule_")
targets = [t for t in result if t.startswith(target_prefixes)]
print("目标表数量:", len(targets))
for t in sorted(targets):
    print(f"  {t}: {len(result[t])} cols")
