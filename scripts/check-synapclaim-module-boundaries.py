#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""synapClaim 三模块依赖边界机检（M1 / M4，D3 模块边界）。

判据（任一破坏即判红）：
  B1 三个模块（sdk / engine / app）pom 与主源码目录齐备，且各模块确实有 Java 源码
     ——空壳模块会被 M2「登记即存在」放行，但「填了契约」这件事必须单独验。
  B2 synap-claim-sdk 编译期零依赖：只允许 junit-jupiter 且 scope=test。
  B3 adjustment-engine 编译期只允许 synap-claim-sdk（其余仅 test scope）。
  B4 app 单向依赖：app 显式依赖 sdk + engine；sdk / engine 不得依赖 app。
  B5/B6 主源码 import 白名单：sdk 只允许 java.* 与 com.bone.claim.sdk.*；
     engine 额外允许 com.bone.claim.engine.*。框架 / 元数据 SDK / 领域层一律禁止。
  B7 主源码不得出现框架注解（@Component / @Autowired / @Table / @Entity 等）。
  B8 sdk / engine 不得出现 application / infrastructure / adapter / domain 包目录
     ——纯契约与纯函数内核不该长成四层应用。

用法：
    python3 scripts/check-synapclaim-module-boundaries.py
    python3 scripts/check-synapclaim-module-boundaries.py --self-test

--self-test 在临时目录重建最小模块树并逐个注入违规，断言每个负向探针都判红；
判据恒绿（把违规当通过）是这类门禁最常见的失效形态，故必须与正向同批自证。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET

REPO = pathlib.Path(__file__).resolve().parent.parent
BASE = pathlib.Path("bone-apps/synap-claim")
MODULES = ("synap-claim-sdk", "adjustment-engine", "synap-claim-app")
SDK_IMPORT_PREFIXES = ("java.", "com.bone.claim.sdk.")
ENGINE_IMPORT_PREFIXES = SDK_IMPORT_PREFIXES + ("com.bone.claim.engine.",)
FRAMEWORK_ANNOTATIONS = re.compile(
    r"@(Component|Service|Repository|Configuration|Controller|Bean|Autowired|Table|Entity|Column|Version)\b"
)
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
LAYER_PACKAGES = ("application", "infrastructure", "adapter", "domain")


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def dependencies(pom: pathlib.Path) -> list[tuple[str, str, str]]:
    if not pom.exists():
        return []
    root = ET.parse(pom).getroot()
    result = []
    for node in root.findall(".//{*}dependency"):
        values = {local(child.tag): (child.text or "").strip() for child in node}
        result.append(
            (
                values.get("groupId", ""),
                values.get("artifactId", ""),
                values.get("scope", "compile"),
            )
        )
    return result


def main_sources(root: pathlib.Path, module: str) -> list[pathlib.Path]:
    src = root / BASE / module / "src/main/java"
    return sorted(src.rglob("*.java")) if src.exists() else []


def check(root: pathlib.Path) -> list[str]:
    problems: list[str] = []

    # B1 模块齐备且非空壳
    for module in MODULES:
        pom = root / BASE / module / "pom.xml"
        if not pom.exists():
            problems.append(f"B1 缺少模块 pom：{module}")
        if not main_sources(root, module):
            problems.append(f"B1 模块 {module} 主源码为空（契约/内核未落地）")

    poms = {m: root / BASE / m / "pom.xml" for m in MODULES}
    deps = {m: dependencies(p) for m, p in poms.items()}
    artifacts = {m: {a for _, a, _ in deps[m]} for m in MODULES}

    # B2 SDK 编译期零框架依赖
    for group, artifact, scope in deps["synap-claim-sdk"]:
        if scope != "test":
            problems.append(f"B2 SDK 出现编译期依赖 {group}:{artifact}（契约模块须零框架依赖）")
        elif artifact != "junit-jupiter":
            problems.append(f"B2 SDK 出现非预期测试依赖 {artifact}")

    # B3 engine 仅依赖 SDK
    for group, artifact, scope in deps["adjustment-engine"]:
        if scope == "test":
            if artifact != "junit-jupiter":
                problems.append(f"B3 engine 出现非预期测试依赖 {artifact}")
        elif artifact != "synap-claim-sdk":
            problems.append(f"B3 engine 出现禁止的编译期依赖 {group}:{artifact}")

    # B4 app 单向依赖
    for required in ("synap-claim-sdk", "adjustment-engine"):
        if required not in artifacts["synap-claim-app"]:
            problems.append(f"B4 app 未声明对 {required} 的依赖（单向依赖链断裂）")
    for upstream in ("synap-claim-sdk", "adjustment-engine"):
        if "synap-claim-app" in artifacts[upstream]:
            problems.append(f"B4 {upstream} 反向依赖 app（边界倒置）")

    # B5/B6/B7 import 与注解白名单
    for module, allowed in (
        ("synap-claim-sdk", SDK_IMPORT_PREFIXES),
        ("adjustment-engine", ENGINE_IMPORT_PREFIXES),
    ):
        for source in main_sources(root, module):
            text = source.read_text(encoding="utf-8")
            rel = source.relative_to(root)
            for imported in IMPORT_RE.findall(text):
                if not imported.startswith(allowed):
                    problems.append(
                        f"B5/B6 {rel} 引入越界包 {imported}（允许前缀 {', '.join(allowed)}）"
                    )
            if FRAMEWORK_ANNOTATIONS.search(text):
                problems.append(f"B7 {rel} 出现框架/持久化注解（纯 Java 模块不得带框架语义）")

    # B8 分层包目录不得出现在纯模块
    for module in ("synap-claim-sdk", "adjustment-engine"):
        src = root / BASE / module / "src/main/java"
        if not src.exists():
            continue
        for layer in LAYER_PACKAGES:
            if any(p.is_dir() and p.name == layer for p in src.rglob(layer)):
                problems.append(f"B8 {module} 出现 {layer}/ 包目录（纯模块不应复制四层结构）")

    return problems


def _fixture(root: pathlib.Path) -> None:
    """在临时目录重建最小但真实的模块树（直接复用仓库 pom 与源码节选）。"""
    for module in MODULES:
        target = root / BASE / module
        target.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(REPO / BASE / module / "pom.xml", target / "pom.xml")
        pkg = target / "src/main/java/com/bone/claim"
        pkg.mkdir(parents=True, exist_ok=True)
        label = module.replace("-", "_")
        (pkg / f"{label}Marker.java").write_text(
            f"package com.bone.claim;\n\nfinal class {label}Marker {{}}\n", encoding="utf-8"
        )


def self_test() -> int:
    failures: list[str] = []

    def expect_red(name: str, rule: str, mutate) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            _fixture(root)
            mutate(root)
            problems = check(root)
            matched = [p for p in problems if p.startswith(rule)]
            if not matched:
                failures.append(
                    f"{name} 未由 {rule} 判红（判据恒绿或被其它规则掩盖）：{problems}"
                )
            else:
                print(f"  PASS 负向探针 {name}: {matched[0]}")

    with tempfile.TemporaryDirectory() as tmp:
        root = pathlib.Path(tmp)
        _fixture(root)
        baseline = check(root)
        if baseline:
            failures.append(f"基线 fixture 应全绿，实际：{baseline}")
        else:
            print("  PASS 正向基线：三模块边界合规")

    def add_sdk_spring(root: pathlib.Path) -> None:
        pom = root / BASE / "synap-claim-sdk/pom.xml"
        text = pom.read_text(encoding="utf-8")
        pom.write_text(
            text.replace(
                "</dependencies>",
                "<dependency><groupId>org.springframework.boot</groupId>"
                "<artifactId>spring-boot-starter</artifactId></dependency></dependencies>",
            ),
            encoding="utf-8",
        )

    def add_engine_spring(root: pathlib.Path) -> None:
        pom = root / BASE / "adjustment-engine/pom.xml"
        text = pom.read_text(encoding="utf-8")
        pom.write_text(
            text.replace(
                "</dependencies>",
                "<dependency><groupId>org.springframework</groupId>"
                "<artifactId>spring-context</artifactId></dependency></dependencies>",
            ),
            encoding="utf-8",
        )

    def drop_app_engine_dep(root: pathlib.Path) -> None:
        pom = root / BASE / "synap-claim-app/pom.xml"
        text = pom.read_text(encoding="utf-8")
        text = re.sub(
            r"<dependency>\s*<groupId>com\.bone\.claim</groupId>\s*"
            r"<artifactId>adjustment-engine</artifactId>.*?</dependency>",
            "",
            text,
            flags=re.S,
        )
        pom.write_text(text, encoding="utf-8")

    def reverse_dependency(root: pathlib.Path) -> None:
        pom = root / BASE / "synap-claim-sdk/pom.xml"
        text = pom.read_text(encoding="utf-8")
        pom.write_text(
            text.replace(
                "</dependencies>",
                "<dependency><groupId>com.bone.claim</groupId>"
                "<artifactId>synap-claim-app</artifactId></dependency></dependencies>",
            ),
            encoding="utf-8",
        )

    def add_sdk_framework_import(root: pathlib.Path) -> None:
        marker = root / BASE / "synap-claim-sdk/src/main/java/com/bone/claim/synap_claim_sdkMarker.java"
        marker.write_text(
            "package com.bone.claim;\n\nimport org.springframework.stereotype.Component;\n\n"
            "final class synap_claim_sdkMarker {}\n",
            encoding="utf-8",
        )

    def add_engine_framework_annotation(root: pathlib.Path) -> None:
        marker = root / BASE / "adjustment-engine/src/main/java/com/bone/claim/adjustment_engineMarker.java"
        marker.write_text(
            "package com.bone.claim;\n\n@Component final class adjustment_engineMarker {}\n",
            encoding="utf-8",
        )

    def add_sdk_application_package(root: pathlib.Path) -> None:
        (root / BASE / "synap-claim-sdk/src/main/java/com/bone/claim/application").mkdir(
            parents=True, exist_ok=True
        )

    expect_red("B2 SDK 编译期依赖", "B2", add_sdk_spring)
    expect_red("B3 engine 编译期框架依赖", "B3", add_engine_spring)
    expect_red("B4 app 删除 engine 依赖", "B4", drop_app_engine_dep)
    expect_red("B4 SDK 反向依赖 app", "B4", reverse_dependency)
    expect_red("B5 SDK 越界 import", "B5", add_sdk_framework_import)
    expect_red("B7 engine 框架注解", "B7", add_engine_framework_annotation)
    expect_red("B8 SDK 四层包目录", "B8", add_sdk_application_package)

    if failures:
        for f in failures:
            print(f"  FAIL {f}", file=sys.stderr)
        return 1
    print("  self-test: 7/7 负向探针判红，基线全绿")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--root", type=pathlib.Path, default=REPO)
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    problems = check(args.root)
    if problems:
        for p in problems:
            print(f"❌ {p}", file=sys.stderr)
        print(f"\nsynapClaim 模块边界机检失败：{len(problems)} 项", file=sys.stderr)
        return 1
    print("✅ synapClaim 模块边界机检通过（B1–B8：SDK 零框架 / engine 纯 JDK / app 单向依赖）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
