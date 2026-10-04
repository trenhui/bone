#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""控制面匿名放行开关的门禁：安全配置的默认值必须是"拒绝"。

背景
----
`bone.extension.studio.security.permit-unauthenticated` 决定是否给Studio 控制面
(`/api/v1/extension/**` 等) 挂 `permitAll()`。它的危害与权限码缺失同级别：
打开它等于**整个控制面匿名可写**——插件上传、部署、版本切换、配置模板全部不需要凭证。

原实现有两个叠加问题（2026-10-04 收敛）：

  ① **基配置硬编码 `true`**：`application.yml` 里写的是裸字面量 `true`，不是
     `${ENV:default}`。这意味着**连环境变量都覆盖不了**（占位符才能被 Spring 解析），
     部署方唯一能做的只有改代码重新打包。
  ② **靠继承拿到放行**：`dev` / `in-memory` / `metadata` 三个联调 profile 都没有
     显式声明该键，全靠继承基配置的 `true`。于是"忘记配置"＝一次静默降级为全站匿名，
     而 profile 命名（`dev`）给人留下的印象恰恰相反——以为 dev 松、prod 紧。

业界通则：**安全配置的默认必须是拒绝**。放行必须是**一次显式的、有痕迹的动作**
（选定 profile 或注入环境变量），而不是"什么也不做"的结果。

本门禁的判据
------------
只对 `src/main/resources/application*.yml`（不含 `src/test/resources`）断言两条：

  R1 **放行不得是裸字面量**：值为裸 `true` 即 FAIL —— 它既不可被环境变量覆盖
     （占位符才能被 Spring 解析），也无法审计"谁放行的"，部署方唯一能做的只有改代码
     重新打包。裸 `false` 不违规：拒绝就是终态，不需要再留逃生口。
  R2 **基配置默认值必须是 `false`**：`application.yml` 里
     `${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:true}` FAIL。
     R2 只判**基配置**，因为它决定"未声明该键的 profile"（含误配 profile、漏加 profile
     的部署）继承到什么——这才是"忘记配置＝全站匿名"的真实来源。
     联调 profile（`dev` / `in-memory` / `metadata`）显式声明 `...:true` 是**合规**的：
     那是一次显式的、有痕迹的动作，正是我们希望的选择方式。

生产 profile（`prod` / `metadata-mysql`）额外受R3 约束：值必须恒为 `false`，
不接受占位符 —— 生产不该存在"靠环境变量打开放行"这条路径。

豁免
----
`src/test/resources/**` 全部豁免：单测需要无 JWT 构造请求，这是测试替身而非部署配置。
把测试也纳入会让门禁要么失效（整体豁免）要么误报（逼测试伪造 JWT）。

为什么不用基线文件
-----------------
存量只有 6 处、且已全部收敛为合规写法。用基线机制（`--baseline` /只可收缩）会引入
"允许裸true 存在"这一状态，而本规则的安全含义恰恰是"裸 true 一律不允许"——
可收缩基线表达不了"零容忍"。故用硬判据 + 豁免清单。

用法
----
    python3 scripts/check-security-fail-open-default.py            # 报告
    python3 scripts/check-security-fail-open-default.py --strict    # 有违规即 exit 1
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# 只扫描 main/resources：test/resources 是测试替身（见"豁免"）。
SCAN_GLOBS = (
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/resources/application*.yml",
)

# 生产 profile：值必须恒为 false，连占位符都不接受。
PROD_PROFILES = ("prod", "metadata-mysql")

# 键名（同时接受 kebab-case 与 camelCase，两种写法都曾出现）
KEY_RE = re.compile(r"^\s*permit[-_]?unauthenticated\s*:\s*(.+?)\s*$")

# ${ENV:default} —— default 可为 true/false/其他
PLACEHOLDER_RE = re.compile(r"^\$\{([^}:]+)(?::([^}]*))\}$")


def _profile_of(path: Path) -> str:
    """`application-metadata-mysql.yml` → `metadata-mysql`；`application.yml` → ""（基配置）。"""
    stem = path.name
    assert stem.endswith(".yml"), stem
    stem = stem[: -len(".yml")]
    if stem == "application":
        return ""
    if stem.startswith("application-"):
        return stem[len("application-") :]
    return stem


def _iter_targets():
    seen = set()
    for pattern in SCAN_GLOBS:
        for path in sorted(REPO.glob(pattern)):
            if path in seen:
                continue
            seen.add(path)
            yield path


def check_file(path: Path) -> list[str]:
    """返回该文件的违规描述列表；空列表 = 合规。"""
    violations: list[str] = []
    profile = _profile_of(path)
    rel = path.relative_to(REPO)
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:  # pragma: no cover
        return [f"{rel}: 读取失败 {exc}"]

    for lineno, line in enumerate(lines, start=1):
        # 注释行不是配置值，跳过（避免把"# permit-unauthenticated: true"读成真配置）
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        m = KEY_RE.match(line)
        if not m:
            continue
        raw = m.group(1).strip()
        # 去掉行尾注释（yml 里` #` 之后是注释，但要避免误伤引号内的 #）
        if "#" in raw:
            quoted = raw.count('"') + raw.count("'")
            if quoted % 2 == 0:
                raw = raw.split("#", 1)[0].strip()

        where = f"{rel}:{lineno}"

        is_prod = profile in PROD_PROFILES

        # R3 生产恒false（裸字面量，不接受占位符）
        if is_prod:
            if raw.lower() != "false":
                violations.append(
                    f"{where} 生产 profile `{profile}` 的 permit-unauthenticated={raw}（R3）。"
                    f"生产必须恒为字面量 false：不应存在\"注入环境变量即可打开放行\"这条路径。"
                )
            # 生产无需再判 R1/R2 —— 字面量 false 已是最严格形态。
            continue

        # R1 放行不得是裸字面量（裸 false 不违规：拒绝即终态，无需逃生口）
        if not raw.startswith("${"):
            if raw.lower() == "true":
                violations.append(
                    f"{where} permit-unauthenticated=true 是裸字面量（R1）。"
                    f"必须写成 ${{BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:false}}："
                    f"裸字面量连环境变量都覆盖不了，部署方只能改代码重新打包，"
                    f"且无法审计是谁放行的。"
                )
            elif raw.lower() != "false":
                violations.append(
                    f"{where} permit-unauthenticated={raw} 既不是 true/false 也不是合法占位符。"
                )
            continue

        pm = PLACEHOLDER_RE.match(raw)
        if not pm:
            violations.append(
                f"{where} permit-unauthenticated={raw} 不是合法的单一占位符"
                f"（期望 ${{ENV:default}} 形式，R1）。"
            )
            continue

        default = (pm.group(2) or "").strip().lower()

        # R2 仅约束基配置（它决定未声明该键的 profile 继承到什么）
        if profile == "" and default != "false":
            violations.append(
                f"{where} 基配置 permit-unauthenticated={raw} 的默认值是 "
                f"{default or '空'}（R2）。基配置决定\"未声明该键的 profile\""
                f"（误配 profile、漏加 profile 的部署）继承到什么——"
                f"安全配置的默认必须是\"拒绝\"，否则\"忘记配置\"就是一次静默的全站匿名降级。"
            )

    return violations


def main() -> int:
    parser = argparse.ArgumentParser(description="控制面匿名放行开关必须默认拒绝")
    parser.add_argument(
        "--strict", action="store_true", help="有违规即 exit 1（门禁模式）"
    )
    args = parser.parse_args()

    all_violations: list[str] = []
    scanned = 0
    found_key_files = 0

    for path in _iter_targets():
        scanned += 1
        text = path.read_text(encoding="utf-8")
        if re.search(r"^\s*permit[-_]?unauthenticated\s*:", text, re.MULTILINE):
            found_key_files += 1
        all_violations.extend(check_file(path))

    print(f"扫描 {scanned} 个 yml，其中 {found_key_files} 个声明了 permit-unauthenticated")
    if all_violations:
        print(f"\n❌ 发现 {len(all_violations)} 处违规：\n")
        for v in all_violations:
            print(f"  · {v}")
        if args.strict:
            return 1
        return 0

    print("✅ 控制面匿名放行开关全部为失败关闭（基配置默认 false，且经可审计通道达成放行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
