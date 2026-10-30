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
  D6 append-only 表（本文 §9.2 矩阵名单）不得注入 updated_at/updated_by/deleted/version
  D7 §9.5 建表数 = 35（与 §9.1 表清单口径一致；改表数须同步 §9.1/§11.13）
"""
import io, re, sys

import _manifest as M

# 路径取自 docs.manifest.json（doc id），不在此硬编码文件名。
DOC = M.path('contract.landing')
# 本文 §9.2「通用列约定」矩阵登记的 append-only 表名单
APPEND_ONLY = {
    'claim_case_track', 'claim_decision_trace', 'claim_decision_execution',
    'claim_rule_hit', 'claim_review_record', 'claim_inspection',
    'settle_funding_event', 'settle_receipt', 'quota_transaction',
    'domain_event_outbox', 'reconciliation_report', 'reconciliation_diff',
    'integration_sync_log',
    'idempotency_record',  # 同矩阵：不注入 updated/version/deleted（TTL 清理）
}
COL_RE = re.compile(r'^  ([a-z_]+)\s+(BIGINT|VARCHAR|DECIMAL|DATETIME|DATE|TINYINT|INT|JSON|CHAR)\b')
KEY_RE = re.compile(r'^  (?:UNIQUE )?KEY\s+\S+\s*\(([^)]*)\)')
MUTABLE_COLS = {'updated_at', 'updated_by', 'deleted', 'version'}


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
        if n in APPEND_ONLY:
            bad = MUTABLE_COLS & set(t['cols'])
            if bad:
                errors.append('D6 %s（L%d）：append-only 表禁注入 %s（§9.2 矩阵：不注入 updated/version/deleted）'
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
