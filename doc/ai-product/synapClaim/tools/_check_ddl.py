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
  D8 §9.0（4）汇总的「5 一致 / 7 仅审计列 / 14 业务列差异」与 §9.5 建表块数自洽，
     且 §9.0（5）台账行数、表名集合与 _audit_ddl_vs_db.BASELINE_BUSINESS_DIFF 相等
  D9 §9.5 标题与正文声明「设计态 / 非执行真源」并指向体检脚本；§9.0 保留
     「为什么不做成硬门禁」的理由

关于 D8/D9（2026-11-09 补）：
  这两条补的是 D7 管不到的一类漂移。D7 只管「有哪些表」，不管「每张表差几列」；
  而 §9.0（4）的 14/23/28 是 _audit_ddl_vs_db.py 跑出来的**结论**，脚本只打印、
  从不回读文档——文档数字被手改成 6/7/13，报告仍打印"含业务列差异 14 张"，
  两边都"没错"。更要紧的是（5）台账的 14 张表与脚本 ratchet 基线 BASELINE_BUSINESS_DIFF
  是**同一事实的两份拷贝**（这正是本仓反复出过的事故形态），此前全靠人工同步。
  D8 只做不连库也能算的那部分；真连库求差仍是 report-only（CI 无 DB，见 §9.0（4））。

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
import io, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import _manifest as M

try:  # D8 要拿 ratchet 基线做集合对账；import 失败即判红，不静默跳过
    from _audit_ddl_vs_db import BASELINE_BUSINESS_DIFF as BASELINE, declared_counts
    _IMPORT_ERR = None
except Exception as e:  # noqa: BLE001
    BASELINE, declared_counts, _IMPORT_ERR = None, None, e

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


def ledger_tables(lines):
    """§9.0（5）整改台账第一列的表名，归一化为逻辑名（去 `t_` 前缀）。

    归一只去 `t_`，不能去 `t_adj_`：台账写 `t_adj_liability`、正文与脚本写
    `adj_liability`，把 `t_adj_` 一起吃掉会得到 `liability`，与基线对不上（已踩）。
    """
    try:
        a = next(i for i, l in enumerate(lines) if l.startswith('#### （5）'))
    except StopIteration:
        return set()
    b = next((i for i in range(a + 1, len(lines))
              if lines[i].startswith('#### ') or lines[i].startswith('### ')), len(lines))
    names = set()
    for l in lines[a:b]:
        if not l.startswith('|'):
            continue
        cells = [c.strip() for c in l.strip().strip('|').split('|')]
        if len(cells) < 6:  # 台账 6 列：表 | 活库多列 | §9.5 多列 | 定性 | 处置 | 优先级
            continue
        m = re.fullmatch(r'`([a-z_0-9]+)`', cells[0])
        if m:
            names.add(re.sub(r'^t_', '', m.group(1)))
    return names


def d8_errors(lines, ddl_names, baseline=None):
    """§9.0 对账结论的自洽性（不连库）。baseline 可注入，便于自证反例。"""
    errs = []
    base = BASELINE if baseline is None else baseline
    if declared_counts is None:
        return ['D8 无法 import _audit_ddl_vs_db（%s）——汇总表解析与 ratchet 基线都拿不到，'
                '不得按"跳过"放行' % _IMPORT_ERR]
    # 必须用传入的 lines 重算，不能回读 DOC：回读会让自证里改过的变异体失效
    #（2026-11-09 初版即如此，变异⑥ 因此假绿——探针抓的是判据没跑，不是判据松）
    declared = declared_counts('\n'.join(lines))
    if declared is None:
        return ['D8 未能从 §9.0（4）汇总表解析出「列完全一致 / 仅审计列差异 / 含业务列差异」三行——'
                '格式已改，先修解析再看结论（缺项不得当 0）']
    total = sum(declared.values())
    if total != len(ddl_names):
        errs.append('D8 §9.0（4）三类相加 = %d（%s），§9.5 实有 %d 个 CREATE TABLE——'
                    '§9.5 增删表后未同步汇总表'
                    % (total, ' + '.join('%s%d' % (k, declared[k]) for k in sorted(declared)),
                       len(ddl_names)))
    ledger = ledger_tables(lines)
    if not ledger:
        errs.append('D8 未能从 §9.0（5）整改台账解析出任何表名——台账被删或格式已改')
        return errs
    if len(ledger) != declared['含业务列差异']:
        errs.append('D8 §9.0（5）台账 %d 行，但（4）声明「含业务列差异」%d 张——'
                    '台账增删行后未同步汇总' % (len(ledger), declared['含业务列差异']))
    if base is None:
        errs.append('D8 取不到 _audit_ddl_vs_db.BASELINE_BUSINESS_DIFF（import 失败）')
        return errs
    if ledger - base:
        errs.append('D8 §9.0（5）台账有、ratchet 基线无：%s——'
                    '漏进基线则该表将来漂移不会被判红' % sorted(ledger - base))
    if base - ledger:
        errs.append('D8 ratchet 基线有、§9.0（5）台账无：%s——台账漏登记' % sorted(base - ledger))
    return errs


def d9_errors(lines):
    """§9.5 的时态声明不得被删回「可执行 DDL」的读法。"""
    errs = []
    a, b = section(lines, '### 9.5')
    head = lines[a]
    if '设计态' not in head and '非执行真源' not in head:
        errs.append('D9 §9.5 标题未声明时态：%r——标题是目录与大纲里唯一的入口，'
                    '「核心表 DDL」会被读成可执行脚本' % head)
    body = '\n'.join(lines[a:b])
    if '设计态' not in body:
        errs.append('D9 §9.5 正文缺「设计态」时态声明（横幅被删？）')
    if '_audit_ddl_vs_db.py' not in body:
        errs.append('D9 §9.5 未指向 tools/_audit_ddl_vs_db.py——'
                    '复跑入口消失后，那 14 张表的差异清单就只能靠记忆维护')
    a0, b0 = section(lines, '### 9.0')
    if '硬门禁' not in '\n'.join(lines[a0:b0]):
        errs.append('D9 §9.0 未记录「为什么不做成硬门禁」——后来者会把列级对账接进 CI，'
                    '用「没起数据库」制造构建失败，与误报同族')
    return errs


def self_test():
    """判别力反证：D7 集合对账 + D8/D9 数字与主张。「全绿」不等于有判别力。"""
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

    print('=== D8/D9 判别力（§9.0 对账数字 + §9.5 时态声明）===')
    expect('正例：汇总 5/7/14 相加 = §9.5 表数，台账 14 行 = 基线集合',
           d8_errors(lines, expected) == [])

    # 变异⑥：只改汇总表一个数字（14 → 13），模拟"改了 DDL 但忘了同步结论"
    bad_sum = [l.replace('| **14** | 见下表', '| **13** | 见下表') for l in lines]
    e6 = d8_errors(bad_sum, expected)
    expect('变异⑥ §9.0（4）含业务列差异 14→13 → 判红（相加不符 + 台账行数不符）',
           any('三类相加' in e for e in e6) and any('台账' in e for e in e6))

    # 变异⑦：台账删掉一行，汇总表仍写 14
    a5 = next(i for i, l in enumerate(lines) if l.startswith('#### （5）'))
    b5 = next((i for i in range(a5 + 1, len(lines))
               if lines[i].startswith('#### ') or lines[i].startswith('### ')), len(lines))
    drop = next(i for i in range(a5, b5) if '`t_quota_transaction`' in lines[i])
    no_row = lines[:drop] + lines[drop + 1:]
    expect('变异⑦ 台账删掉一行（汇总仍 14）→ 判红',
           any('台账' in e for e in d8_errors(no_row, expected)))

    # 变异⑧：基线少一张 —— 台账与 ratchet 是同一事实的两份拷贝，必须双向相等
    expect('变异⑧ ratchet 基线漏掉一张 → 判红（否则该表漂移不报案）',
           any('ratchet 基线无' in e
               for e in d8_errors(lines, expected, baseline=set(BASELINE) - {'claim_case'})))

    a95, _ = section(lines, '### 9.5')
    old_head = lines[a95]
    reverted = lines[:a95] + ['### 9.5 核心表 DDL（MySQL 8.0 / TiDB 同构）'] + lines[a95 + 1:]
    expect('变异⑨ §9.5 标题退回「核心表 DDL」→ 判红（标题是目录里唯一的入口）',
           any('标题未声明时态' in e for e in d9_errors(reverted)))
    expect('正例：§9.5 标题已声明时态 / 正文指向体检脚本 / §9.0 保留硬门禁理由',
           d9_errors(lines) == [] and '设计态' in old_head)

    a90, b90 = section(lines, '### 9.0')
    no_reason = lines[:a90] + [l for l in lines[a90:b90] if '硬门禁' not in l] + lines[b90:]
    expect('变异⑩ 删掉「为什么不做成硬门禁」→ 判红（后来者会把它接进 CI）',
           any('硬门禁' in e for e in d9_errors(no_reason)))

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
    errors.extend(d8_errors(lines, {t['name'] for t in tables}))
    errors.extend(d9_errors(lines))

    if errors:
        print('=== DDL 可执行性机检 ===')
        for e in errors:
            print('【FAIL】' + e)
        print('共 %d 项失败' % len(errors))
        return 1
    print('=== DDL 可执行性机检 ===')
    print('【OK】%d 表全部通过：单 id 主键 / 列名唯一 / PK 存在 / 索引列已声明 / 围栏收尾 / '
          'append-only 无变更列 / §9.1 清单与 §9.0 差异登记的集合对账 / '
          '§9.0 对账数字与台账·ratchet 基线三方一致 / §9.5 时态声明在位' % len(tables))
    return 0


if __name__ == '__main__':
    sys.exit(self_test() if '--self-test' in sys.argv else main())
