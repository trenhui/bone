#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""配置键契约检查（MVP-10 质量下沉 ②）。

为什么需要：`@Value("${some.key:default}")` 里的键名写错时，Spring **静默回落**到默认值——
不报错、不打日志，症状出现在离故障点很远的地方（例如 JWT 密钥键名不一致表现为全链路 401，
而日志里只看到「验签失败」）。单测与 ArchUnit 都抓不到，只能靠键名契约检查。

判定口径：
  - 模块 yml（src/main/resources/application*.yml + 各 profile 变体）中定义的键 = 已知键；
  - 代码引用的键若**不在已知键内且未给默认值** → FAIL（启动时直接抛占位符解析失败，或回落成空）；
  - 不在已知键内但**给了默认值** → WARN（很可能是笔误，但不会启动失败）。

用法：
    python3 scripts/ci/check-config-key-contract.py            # 全仓
    python3 scripts/ci/check-config-key-contract.py --strict    # WARN 也计为失败

不依赖 pyyaml（本环境未安装），用缩进栈自行解析 yml。
"""

from __future__ import annotations

import argparse
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

VALUE_RE = re.compile(r'@Value\(\s*"\$\{([^}:]+)(?::([^}]*))?\}"\s*\)')
PROPS_RE = re.compile(r'@ConfigurationProperties\(\s*prefix\s*=\s*"([^"]+)"')
# 形如 ${key} 的嵌套引用（yml 内部），同样可能是笔误来源
YML_REF_RE = re.compile(r"\$\{([^}:]+)(?::([^}]*))?\}")

MODULES = [
    "bone-platform/bone-iam",
    "bone-platform/bone-system",
    "bone-platform/bone-masterdata",
    "bone-platform/bone-integration",
    "bone-platform/bone-gateway",
    "bone-engine/bone-metadata-server",
    "bone-engine/studio-generator",
    "bone-engine/bone-extension-studio",
]


def yml_keys(path: str) -> set:
    """按缩进解析 yml，返回点分键集合（含中间路径）。"""
    keys: set = set()
    stack: list = []  # [(indent, key)]
    try:
        lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
    except OSError:
        return keys
    for raw in lines:
        line = raw.split("#", 1)[0].rstrip() if not raw.strip().startswith("#") else ""
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        body = line.strip()
        if body.startswith("- "):  # 列表项：不作为键
            continue
        if ":" not in body:
            continue
        key = body.split(":", 1)[0].strip()
        if not key:
            continue
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        full = ".".join(k for _i, k in stack)
        keys.add(full)
    return keys


def collect_known_keys(module: str) -> set:
    known: set = set()
    res = os.path.join(ROOT, module, "src", "main", "resources")
    if not os.path.isdir(res):
        return known
    for fn in os.listdir(res):
        if fn.startswith("application") and fn.endswith((".yml", ".yaml")):
            known |= yml_keys(os.path.join(res, fn))
    return known


def scan_java(module: str):
    refs = []  # (file, line, key, has_default)
    src = os.path.join(ROOT, module, "src", "main", "java")
    if not os.path.isdir(src):
        return refs
    for root, _dirs, files in os.walk(src):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            fp = os.path.join(root, fn)
            try:
                text = open(fp, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for i, line in enumerate(text.split("\n"), 1):
                for key, default in VALUE_RE.findall(line):
                    refs.append((os.path.relpath(fp, ROOT), i, key.strip(), default is not None))
    return refs


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="把 WARN（有默认值但键未知）也计为失败")
    args = ap.parse_args()

    failures, warnings = [], []
    total_refs = 0

    for module in MODULES:
        if not os.path.isdir(os.path.join(ROOT, module)):
            continue
        known = collect_known_keys(module)
        if not known:
            print(f"  [SKIP] {module}：无 application*.yml")
            continue
        refs = scan_java(module)
        total_refs += len(refs)
        for file, line, key, has_default in refs:
            if key in known:
                continue
            # 允许跨模块共享前缀（如 bone.core.* 由 bone-core 定义）在其它模块的 yml 里出现
            item = f"{module}: {file}:{line} → ${{{key}}}"
            if has_default:
                warnings.append(item)
            else:
                failures.append(item)

    print(f"配置键契约检查：引用 {total_refs} 处，FAIL {len(failures)}，WARN {len(warnings)}")
    for f in failures:
        print(f"  [FAIL] 键未在 yml 定义且无默认值（启动即失败）：{f}")
    for w in warnings:
        print(f"  [WARN] 键未在 yml 定义但有默认值（疑似笔误，会静默回落）：{w}")

    if failures:
        return 1
    if args.strict and warnings:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
