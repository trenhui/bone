#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""统一造数入口 —— 任何 agent 不写脚本也能拿到「像生产」的测试数据。

解决的问题
----------
以前每个 agent 为了造一批数据，都要自己写一个 `_seed_xxx.py`：字段名各起一套、
金额随手写圆整数、单号用 `P1/TEST-1`、人名用「张三」。同一功能三个人能造出三套
互不兼容的数据，谁也复用不了谁。本 CLI 把造数收敛为**一次命令**：

    python3 scripts/e2e/datagen.py list                     # 先看有什么（agent 第一步）
    python3 scripts/e2e/datagen.py sales-claim               # 整条业务链（JSON）
    python3 scripts/e2e/datagen.py procurement --seed demo   # 固定 seed ⇒ 可复跑
    python3 scripts/e2e/datagen.py tenant-iam --format md    # 人读表格（评审/截图）
    python3 scripts/e2e/datagen.py fixture person --count 5  # 原子值批量

数据口径由 `e2e_lib/fixtures.py`（词表/金额/守恒）与 `e2e_lib/scenarios.py`
（业务对象拼装/不变量）保证，本文件只做 CLI 编排，不含造数逻辑。

输出格式
--------
  json（默认） 机器消费，直接喂 API / 断言
  env          `export BONE_DATA_...=...`，给 shell / 手工联调用
  md           「字段 | 值」表格，给评审与人看

退出码：0 成功 / 2 参数或场景错误（供编排判定，不静默回退）。
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from e2e_lib import config, fixtures as fx, scenarios  # noqa: E402

FIXTURE_KINDS = {
    "person": lambda ds: ds.person(),
    "company": lambda ds: ds.company(),
    "sku": lambda ds: dict(zip(("sku_name", "unit_price"), ds.sku())),
    "amount": lambda ds: ds.amount(),
    "doc-no": lambda ds: ds.doc_no("DEMO"),
    "tax-no": lambda ds: ds.tax_no(),
    "equipment": lambda ds: dict(zip(("category", "equipment_code", "equipment_name"), ds.equipment())),
}


def _default_seed():
    """默认 seed：显式环境变量优先，否则用本次运行后缀（天然唯一，便于清理归因）。"""
    return os.environ.get("BONE_E2E_SEED") or config.run_suffix()


def _flatten(obj, prefix=""):
    """把嵌套 dict/list 摊平成 [(路径, 值)]，供 env / md 使用。"""
    rows = []
    if isinstance(obj, dict):
        for key, val in obj.items():
            rows.extend(_flatten(val, "%s.%s" % (prefix, key) if prefix else str(key)))
    elif isinstance(obj, list):
        for i, val in enumerate(obj):
            rows.extend(_flatten(val, "%s[%d]" % (prefix, i)))
    else:
        rows.append((prefix, obj))
    return rows


def _env_name(path):
    out = []
    for ch in path.upper():
        out.append(ch if ch.isalnum() else "_")
    name = "".join(out)
    while "__" in name:
        name = name.replace("__", "_")
    return "BONE_DATA_" + name.strip("_")


def _fmt_env(obj):
    lines = []
    for path, val in _flatten(obj):
        if isinstance(val, bool):
            val = "1" if val else "0"
        if val is None:
            val = ""
        lines.append("export %s='%s'" % (_env_name(path), str(val).replace("'", "'\\''")))
    return "\n".join(lines) + "\n"


def _fmt_md(title, obj):
    rows = _flatten(obj)
    width = max([len(p) for p, _ in rows] + [4])
    lines = ["# %s" % title, "", "| 字段 | 值 |", "| %s | --- |" % ("-" * width)]
    for path, val in rows:
        lines.append("| `%s` | %s |" % (path, val))
    return "\n".join(lines) + "\n"


def _emit(obj, fmt, title):
    if fmt == "json":
        print(json.dumps(obj, ensure_ascii=False, indent=2))
    elif fmt == "env":
        sys.stdout.write(_fmt_env(obj))
    elif fmt == "md":
        print(_fmt_md(title, obj))
    else:
        raise ValueError("未知格式: %s" % fmt)


def _cmd_list(args):
    if args.json:
        print(json.dumps(
            {
                "scenarios": [{"name": n, "desc": d} for n, d in scenarios.describe()],
                "fixtures": sorted(FIXTURE_KINDS),
                "library": "e2e_lib/fixtures.py（原子值）+ e2e_lib/scenarios.py（业务场景）",
            },
            ensure_ascii=False,
            indent=2,
        ))
        return 0
    print("可复用造数能力（同 seed ⇒ 同数据；数值口径见 e2e_lib/fixtures.py）\n")
    print("业务场景：")
    for name, desc in scenarios.describe():
        print("  %-14s %s" % (name, desc))
    print("\n原子值（fixture <kind> --count N）：")
    print("  " + ", ".join(sorted(FIXTURE_KINDS)))
    print("\n示例：")
    print("  python3 scripts/e2e/datagen.py sales-claim --seed demo-a")
    print("  python3 scripts/e2e/datagen.py tenant-iam --format md")
    print("  python3 scripts/e2e/datagen.py fixture person --count 5")
    return 0


def _cmd_scenario(args):
    seed = args.seed or _default_seed()
    data = scenarios.build(args.name, seed)
    _emit(data, args.format, "场景 %s（seed=%s）" % (args.name, seed))
    return 0


def _key_of(value):
    """去重键：dict/list 转成可哈希结构，标量原样。"""
    if isinstance(value, dict):
        return tuple(sorted((k, _key_of(v)) for k, v in value.items()))
    if isinstance(value, (list, tuple)):
        return tuple(_key_of(v) for v in value)
    return value


def _unique_values(ds, kind, count):
    """批量取原子值：同 seed 结果稳定，且尽量不出现重复样本。

    随机取值会有碰撞（54 姓 × 51 名里 3 次抽到同一人是可能的），但 `--count 3`
    输出两个「卢娜」会让人以为脚本坏了。碰撞时继续抽，最多试 count×20 次。
    """
    gen = FIXTURE_KINDS[kind]
    out, seen = [], set()
    for _ in range(max(1, count) * 20):
        if len(out) >= max(1, count):
            break
        value = gen(ds)
        key = _key_of(value)
        if key in seen:
            continue
        seen.add(key)
        out.append(value)
    return out


def _cmd_fixture(args):
    seed = args.seed or _default_seed()
    if args.kind not in FIXTURE_KINDS:
        print("未知 fixture: %s（可选: %s）" % (args.kind, ", ".join(sorted(FIXTURE_KINDS))),
              file=sys.stderr)
        return 2
    ds = fx.dataset(seed)
    values = _unique_values(ds, args.kind, args.count)
    obj = {"kind": args.kind, "seed": str(seed), "count": len(values), "values": values}
    _emit(obj, args.format, "fixture %s（seed=%s）" % (args.kind, seed))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="datagen.py",
        description="企业级测试数据统一入口（复用优先，禁止再手写造数脚本）",
    )
    sub = parser.add_subparsers(dest="cmd")

    p_list = sub.add_parser("list", help="列出全部造数能力")
    p_list.add_argument("--json", action="store_true", help="机器可读输出（agent 消费）")
    p_list.set_defaults(func=_cmd_list)

    p_fix = sub.add_parser("fixture", help="生成原子值（person/company/sku/amount/doc-no/tax-no/equipment）")
    p_fix.add_argument("kind")
    p_fix.add_argument("--count", type=int, default=1)
    p_fix.add_argument("--seed")
    p_fix.add_argument("--format", choices=["json", "env", "md"], default="json")
    p_fix.set_defaults(func=_cmd_fixture)

    # 场景子命令：直接以场景名作为命令，避免 `datagen scenario sales-claim` 的额外记忆负担
    for name in scenarios.names():
        p = sub.add_parser(name, help=scenarios.SCENARIOS[name]["desc"])
        p.add_argument("--seed")
        p.add_argument("--format", choices=["json", "env", "md"], default="json")
        p.set_defaults(func=_cmd_scenario, name=name)

    args = parser.parse_args(argv)
    if not getattr(args, "func", None):
        parser.print_help()
        return 0
    try:
        return args.func(args)
    except ValueError as exc:
        print("错误: %s" % exc, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
