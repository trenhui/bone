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
  D7 §9.5 建表集合 = §9.1 清单集合 − §9.0 差异登记（未建/已改名）集合，双向对账
     （原为硬编码 `len(tables) == 36`：删/增一张表都要手改数字，且无法识别
       「删了 A 又补了 B、总数仍 36」这类实质漂移。现改为集合判据，
       唯一真源是 §9.1 清单与 §9.0 差异登记表，脚本不再持有表数。）

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


def section(lines, heading):
    """截取以 heading 开头的小节，到下一个 ### 或 ## 为止（含端点索引）。"""
    a = next(i for i, l in enumerate(lines) if l.startswith(heading))
    b = next((i for i in range(a + 1, len(lines))
              if lines[i].startswith('### ') or lines[i].startswith('## ')), len(lines))
    return a, b


def declared_tables(lines):
    """§9.1 清单第一列里的全部反引号表名（含一行多个表的组合登记）。"""
    a, b = section(lines, '### 9.1')
    names = set()
    for l in lines[a:b]:
        if not l.startswith('|'):
            continue
        names |= set(re.findall(r'`([a-z_0-9]+)`', l.split('|')[1]))
    return names


def stale_tables(lines):
    """§9.0 中「已改名 / 活库无表」两张差异登记表的表名。

    只认（1）（2）两个小节，不按「表格行里出现反引号名」猜语义。
    2026-11-07 踩过：§9.0 后来新增（4）列级对账表，里面列的是**有效表**
    （claim_case / adj_liability …），旧实现把它们全收进 stale，D7 当场误判红。
    教训与 C8 同源：正则要按**语义区间**取数，不能按「长得像」取数。
    """
    a, b = section(lines, '### 9.0')
    names = set()
    active = False
    for l in lines[a:b]:
        if l.startswith('#### '):
            active = ('已改名' in l) or ('活库无表' in l)
            continue
        if not active or not l.startswith('|'):
            continue
        if set(l.replace('|', '').strip()) <= set('-: '):
            continue
        first = l.split('|')[1].strip()
        hits = re.findall(r'`([a-z_0-9]+)`', first)
        # 表头与「活库真名」列（t_ 前缀）不参与
        if len(hits) == 1 and not first.startswith('`t_'):
            names.add(hits[0])
    return names


def d7_errors(lines, ddl_names):
    """集合对账。返回错误列表；解析不出集合时 fail-closed，不静默放行。"""
    declared = declared_tables(lines)
    stale = stale_tables(lines)
    errs = []
    if len(declared) < 20:
        errs.append('D7 未能从 §9.1 清单解析出表集合（%d 张）——清单格式可能已改，先修解析再看结论' % len(declared))
    if not stale:
        errs.append('D7 未能从 §9.0 差异登记表解析出任何「未建/已改名」表——'
                    '差异登记被删或格式已改时须 fail-closed，不得按空集合恒过')
    if errs:
        return errs
    expected = declared - stale
    if expected - ddl_names:
        errs.append('D7 §9.1 清单登记但 §9.5 缺 DDL：%s' % sorted(expected - ddl_names))
    if ddl_names - expected:
        errs.append('D7 §9.5 有 DDL 但 §9.1 未登记（或已在 §9.0 标为未建/改名）：%s'
                    % sorted(ddl_names - expected))
    return errs


def self_test():
    """D7 判别力反证：「全绿」不等于有判别力，必须证明改坏了会判红。"""
    lines = io.open(DOC, encoding='utf-8').read().split('\n')
    expected = declared_tables(lines) - stale_tables(lines)
    passed = 0
    failed = 0

    def expect(label, cond):
        nonlocal passed, failed
        if cond:
            passed += 1
            print('  ✅ ' + label)
        else:
            failed += 1
            print('  ❌ ' + label)

    print('=== _check_ddl.py --self-test（D7 集合对账判别力）===')
    expect('正例：当前 §9.1 − §9.0 与 §9.5 对账无错', d7_errors(lines, expected) == [])
    expect('变异① 把已摘除的 adj_policy DDL 放回来 → 判红',
           any('§9.5 有 DDL' in e for e in d7_errors(lines, expected | {'adj_policy'})))
    expect('变异② 删掉一张有效表的 DDL → 判红',
           any('缺 DDL' in e for e in d7_errors(lines, expected - {'claim_case'})))

    a, b = section(lines, '### 9.0')
    no_registry = lines[:a + 1] + lines[b:]
    expect('变异③ §9.0 差异登记整段被删 → fail-closed，不得按空集合恒过',
           any('fail-closed' in e for e in d7_errors(no_registry, expected)))

    a, b = section(lines, '### 9.1')
    no_inventory = lines[:a + 1] + lines[b:]
    expect('变异④ §9.1 清单整段被删 → fail-closed',
           any('未能从 §9.1' in e for e in d7_errors(no_inventory, expected)))

    # 变异⑤：§9.0 里新增一张与差异登记无关的表格，列的是有效表。
    # 旧实现会把它们全收进 stale，导致 D7 对真实存在的表误判红。
    extra = ['', '#### （9）随便一张审计表', '',
             '| 表 | 结论 |', '|---|---|',
             '| `claim_case` | 一致 |', '| `adj_liability` | 有差异 |', '']
    a, _ = section(lines, '### 9.1')
    with_extra = lines[:a] + extra + lines[a:]
    expect('变异⑤ §9.0 追加无关表格（含有效表名）→ 不得污染 stale 集合',
           stale_tables(with_extra) == stale_tables(lines))

    print('  %d 通过 / %d 失败' % (passed, failed))
    return 0 if failed == 0 else 1


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

    errors.extend(d7_errors(lines, {t['name'] for t in tables}))

    if errors:
        print('=== DDL 可执行性机检 ===')
        for e in errors:
            print('【FAIL】' + e)
        print('共 %d 项失败' % len(errors))
        return 1
    print('=== DDL 可执行性机检 ===')
    print('【OK】%d 表全部通过：单 id 主键 / 列名唯一 / PK 存在 / 索引列已声明 / 围栏收尾 / '
          'append-only 无变更列 / §9.1 清单与 §9.0 差异登记的集合对账' % len(tables))
    return 0


if __name__ == '__main__':
    sys.exit(self_test() if '--self-test' in sys.argv else main())
