#!/usr/bin/env python3
"""门禁 21：OpenAPI 规范的"生成器前置条件"静态校验。

**为什么需要它**：契约的若干缺陷在 `redocly lint` 与 `check-openapi-contract.py` 下
都是全绿的（那两条只管 YAML 可解析 / 认证声明 / `$ref` 可解析），但**生成器会直接拒绝或
产出不可用的代码**。实测两个真实缺陷：

1. `generator-v1.yaml` 的 `PUT/DELETE /data-sources/{id}` 未声明 path 参数 `id`
   ⇒ openapi-generator 抛 `SpecValidationException` **整份规范生成失败**（不是警告）。
   而 `metadata-runtime-v1.yaml` 的5 处"缺参"是 `$ref` 到 `components/parameters`，
   属正常写法 —— **判据必须先解 `$ref`**，否则会把这5 处误报成缺陷（第一版就踩了）。
2. 全部 8 份规范共 90 个 operation 缺 `operationId` 与 `tags`
   ⇒ 生成器只能产出单个 `DefaultApi`，方法名退化成由 path 机械拼接的
   `accountsIdGetPost`（实测 47 个方法全落在 `DefaultApi`），SDK 不可用。

顺带核对 `scripts/generate-sdk.sh` 的 `SPECS` 与磁盘上的规范文件一一对应、
且包名片段等于"规范名去掉 -v1 再去掉 `-`" —— 两边不一致时烟测会以
ClassNotFoundException 报错（实测手工常量多写了一个字母）。

用法：
    check-openapi-generation-readiness.py            # 校验，失败退出 1
    check-openapi-generation-readiness.py --report-only
"""
import os
import re
import sys

import yaml

METH = ("get", "post", "put", "delete", "patch", "head", "options")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC_DIR = os.path.join(ROOT, "doc", "architecture", "openapi")
GEN_SCRIPT = os.path.join(ROOT, "scripts", "generate-sdk.sh")

RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RESET = "\033[0m"


def deref(node, doc):
    """解本文件内的 $ref；跨文件 $ref（./components/*.yaml）返回 None（由别的门禁管）。"""
    if isinstance(node, dict) and "$ref" in node:
        ref = node["$ref"]
        if ref.startswith("#/"):
            cur = doc
            for part in ref[2:].split("/"):
                cur = cur[part]
            return cur
        return None
    return node


def main():
    report_only = "--report-only" in sys.argv
    problems = []
    warnings = []
    stats = {"specs": 0, "ops": 0, "missing_oid": 0, "missing_tags": 0, "missing_path_param": 0}

    spec_files = sorted(f for f in os.listdir(SPEC_DIR) if f.endswith("-v1.yaml"))
    stats["specs"] = len(spec_files)

    for fn in spec_files:
        path = os.path.join(SPEC_DIR, fn)
        doc = yaml.safe_load(open(path, encoding="utf-8"))
        seen_oids = {}
        for p, item in (doc.get("paths") or {}).items():
            shared = [x for x in (deref(y, doc) for y in (item.get("parameters") or [])) if x]
            shared_paths = {pr["name"] for pr in shared if pr.get("in") == "path"}
            for m, op in item.items():
                if m not in METH or not isinstance(op, dict):
                    continue
                stats["ops"] += 1
                where = f"{fn} {m.upper()} {p}"

                oid = op.get("operationId")
                if not oid:
                    stats["missing_oid"] += 1
                    problems.append(f"{where}：缺 operationId（生成器将退化为 path 拼接的方法名）")
                elif oid in seen_oids:
                    problems.append(
                        f"{where}：operationId '{oid}' 与 {seen_oids[oid]} 重复"
                        "（同一 artifact 内会编译冲突）"
                    )
                else:
                    seen_oids[oid] = where

                if not op.get("tags"):
                    stats["missing_tags"] += 1
                    problems.append(f"{where}：缺 tags（生成器会把所有操作塞进单个 DefaultApi）")

                declared = set(shared_paths)
                for pr in (deref(y, doc) for y in (op.get("parameters") or [])):
                    if pr and pr.get("in") == "path":
                        declared.add(pr["name"])
                missing = set(re.findall(r"\{([^}/]+)\}", p)) - declared
                if missing:
                    stats["missing_path_param"] += 1
                    problems.append(
                        f"{where}：path 模板变量 {sorted(missing)} 未声明为 path 参数"
                        "（openapi-generator 会直接抛 SpecValidationException 拒绝整份规范）"
                    )

    # ── generate-sdk.sh 的 SPECS 与磁盘规范一一对应 + 包名片段合规 ──────────────
    if not os.path.isfile(GEN_SCRIPT):
        problems.append("scripts/generate-sdk.sh 不存在（ADR-0040 第 11 项的载体）")
    else:
        src = open(GEN_SCRIPT, encoding="utf-8").read()
        block = re.search(r"SPECS=\((.*?)\n\)", src, re.S)
        if not block:
            problems.append("generate-sdk.sh 里找不到 SPECS=(...) 清单")
        else:
            entries = re.findall(r'"([\w.\-]+):(\w+)"', block.group(1))
            listed = [e[0] for e in entries]
            for fn in spec_files:
                if fn[: -len(".yaml")] not in listed:
                    problems.append(
                        f"generate-sdk.sh 的 SPECS 未登记 {fn}（新增规范漏登记 ⇒ 不会生成 artifact）"
                    )
            for spec, domain in entries:
                if spec not in [f[: -len(".yaml")] for f in spec_files]:
                    warnings.append(f"generate-sdk.sh 登记了不存在的规范 {spec}.yaml")
                    continue
                # 包名片段必须等于推导规则，否则烟测 ClassNotFoundException
                derived = spec.replace("-v1", "").replace("-", "")
                if domain != derived:
                    problems.append(
                        f"generate-sdk.sh 中 {spec} 的包名片段为 '{domain}'，"
                        f"与推导值 '{derived}'（规范名去 -v1 去 '-'）不一致 ⇒ 烟测会 ClassNotFound"
                    )

    print("OpenAPI 生成器前置条件检查")
    print(
        f"  规范 {stats['specs']} 份/ 操作 {stats['ops']} 个"
        f" | 缺 operationId {stats['missing_oid']}"
        f" | 缺 tags {stats['missing_tags']}"
        f" | 缺 path 参数声明 {stats['missing_path_param']}"
    )
    for w in warnings:
        print(f"{YELLOW}⚠ {w}{RESET}")
    for p in problems:
        print(f"{RED}❌ {p}{RESET}")

    if problems and not report_only:
        print(f"{RED}生成器前置条件不满足：{len(problems)} 项缺陷{RESET}")
        return 1
    if problems:
        print(f"{YELLOW}（report-only）{len(problems)} 项缺陷{RESET}")
        return 0
    print(f"{GREEN}✅ 8 份规范均可直接喂给 openapi-generator{RESET}")
    return 0


if __name__ == "__main__":
    sys.exit(main())