#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DDL 可执行性机检（工程落地版 §9.5）。

背景：2026-10-23 诊断发现 §9.5 有 17 张表的外键列被写成 `id`（与主键重名，
MySQL/TiDB 直接 Duplicate column name），且同批索引引用并不存在的 claim_id。
本脚本把该类错误固化为门禁，与 _check_xref.py / _check_contract_drift.py 并列。

检查项（任一失败即非零退出）：
  D1 每个 CREATE TABLE 恰一个 `id` 主键列（外键列禁重用 id）
  D2 表内列名唯一（含重复列名直接报错）
  D3 PRIMARY KEY (id) 存在
  D4 KEY/UNIQUE KEY 引用的列均已声明
  D5 CREATE TABLE 块以 `) ENGINE=InnoDB` 收尾（无悬空逗号）
  D6 禁注入表的建表块不得含 updated_at/updated_by/deleted/version
     （名单**从本文 §9.2 矩阵现读**，不在脚本里手抄——见下方 append_only_tables）
  D7 §9.5 建表数 = 35（与 §9.1 表清单口径一致；改表数须同步 §9.1/§11.13）

关于 D6 的名单来源（2026-10-30 修正）：
  原先本脚本硬编码一份 APPEND_ONLY 副本，注释却写着「本文 §9.2 矩阵登记的名单」——
  注释宣称的与实际做的不是一回事。后果在当天就应验：docs 把
  `claim_decision_execution` 从 append-only 移到可变状态表后，脚本仍按旧副本判红，
  报的是一条**已经不成立**的规则。同一事实存两份，改一处不报错，只是校验的
  不再是同一件事（与上一轮文档族「同一事实四份拷贝」同源）。
  现在改为解析 §9.2 矩阵：判据是「该行 `updated_by/at` 列写着『不注入』」，
  而不是维护一份表名清单——这才是矩阵真正表达的语义。
  矩阵若解析不出任何表，直接判失败（宁可红着让人看，也不要空集合恒过）。
"""
import io, re, sys

import _manifest as M

# 路径取自 docs.manifest.json（doc id），不在此硬编码文件名。
DOC = M.path('contract.landing')
COL_RE = re.compile(r'^  ([a-z_]+)\s+(BIGINT|VARCHAR|DECIMAL|DATETIME|DATE|TINYINT|INT|JSON|CHAR)\b')
KEY_RE = re.compile(r'^  (?:UNIQUE )?KEY\s+\S+\s*\(([^)]*)\)')
MUTABLE_COLS = {'updated_at', 'updated_by', 'deleted', 'version'}


def append_only_tables(lines):
    """从 §9.2「通用列注入矩阵」解析出「禁注入变更列」的表名集合。

    返回 (exact_names, prefix_patterns)；矩阵解析失败返回 (None, None)。
    判据取列语义而非表名清单：`updated_by/at` 列含「不注入」的行，其表名全部计入。
    `reconciliation_*` 这类通配登记转成前缀集合，避免把通配当成一个不存在的表名。
    """
    exact, prefixes, found_row = set(), set(), False
    for l in lines:
        if not l.startswith('|'):
            continue
        cells = [c.strip() for c in l.strip().strip('|').split('|')]
        # 矩阵列序：表类 | tenant_id | created_by/at | updated_by/at | version | deleted
        if len(cells) < 5:
            continue
        if '不注入' not in cells[3]:
            continue
        found_row = True
        for name in re.findall(r'`([a-z_*]+)`', cells[0]):
            if name.endswith('*'):
                prefixes.add(name[:-1])
            else:
                exact.add(name)
    if not found_row or not (exact or prefixes):
        return None, None
    return exact, prefixes


def is_forbidden(name, exact, prefixes):
    if name in exact:
        return True
    return any(name.startswith(p) for p in prefixes)


def main():
    lines = io.open(DOC, encoding='utf-8').read().split('\n')
    start = next(i for i, l in enumerate(lines) if l.startswith('CREATE TABLE claim_case'))
    end = next(i for i, l in enumerate(lines) if l.startswith('## 10'))
    errors = []
    tables = []  # (name, cols, keycols, block_end_ok, has_pk)
    cur = None
    for i in range(start, end):
        l = lines[i]
        if l.startswith('CREATE TABLE'):
            if cur:
                tables.append(cur)
            cur = {'name': l.split()[2], 'cols': [], 'keys': [], 'ok_tail': False, 'pk': False, 'line': i + 1}
            continue
        if cur is None:
            continue
        m = COL_RE.match(l)
        if m:
            cur['cols'].append(m.group(1))
        m2 = KEY_RE.match(l)
        if m2:
            cur['keys'].extend(c.strip() for c in m2.group(1).split(','))
        if l.startswith('  PRIMARY KEY (id)'):
            cur['pk'] = True
        if l.startswith(') ENGINE=InnoDB'):
            cur['ok_tail'] = True
    if cur:
        tables.append(cur)

    exact, prefixes = append_only_tables(lines)
    if exact is None:
        print('=== DDL 可执行性机检 ===')
        print('【FAIL】D6 未能从 §9.2 注入矩阵解析出任何「禁注入」表——'
              '矩阵格式可能已改（表头/列序变化），先修解析再看结论')
        return 1

    for t in tables:
        n = t['name']
        if t['cols'].count('id') != 1:
            errors.append('D1 %s（L%d）：id 主键列出现 %d 次——外键列禁重用 id（应为 claim_id 等语义名）'
                          % (n, t['line'], t['cols'].count('id')))
        dup = sorted({c for c in t['cols'] if t['cols'].count(c) > 1})
        if dup:
            errors.append('D2 %s（L%d）：列名重复 %s' % (n, t['line'], dup))
        if not t['pk']:
            errors.append('D3 %s（L%d）：缺少 PRIMARY KEY (id)' % (n, t['line']))
        if not t['ok_tail']:
            errors.append('D5 %s（L%d）：块未以 ) ENGINE=InnoDB 收尾（悬空逗号/括号不配平）' % (n, t['line']))
        undeclared = [c for c in t['keys'] if c not in t['cols']]
        if undeclared:
            errors.append('D4 %s（L%d）：索引引用未声明列 %s' % (n, t['line'], undeclared))
        if is_forbidden(n, exact, prefixes):
            bad = MUTABLE_COLS & set(t['cols'])
            if bad:
                errors.append('D6 %s（L%d）：§9.2 矩阵判其禁注入变更列，却声明了 %s'
                              % (n, t['line'], sorted(bad)))

    if len(tables) != 35:
        errors.append('D7 §9.5 建表数 %d ≠ 35（与 §9.1 表清单/§11.13 口径不一致）' % len(tables))

    if errors:
        print('=== DDL 可执行性机检 ===')
        for e in errors:
            print('【FAIL】' + e)
        print('共 %d 项失败' % len(errors))
        return 1
    print('=== DDL 可执行性机检 ===')
    print('【OK】35 表全部通过：单 id 主键 / 列名唯一 / PK 存在 / 索引列已声明 / 围栏收尾 / append-only 无变更列')
    return 0


if __name__ == '__main__':
    sys.exit(main())
