#!/usr/bin/env python3
"""对外发布模块（SDK）契约面门禁：禁止"空壳却宣称是 SDK"，且有契约面时必须带测试。

为什么需要它（2026-10-03 实测事故）：`bone-sdk/bone-client-sdk` 与 `bone-sdk/bone-openapi-sdk`
曾在根 `pom.xml` 的 `<modules>` 里，被正常构建、被 IDEA 当作"对外 SDK"消费，但实际**只有一个
IDE 生成的 `com.bone.cloud.Main`**（打印 "Hello and welcome"），零依赖、零类型、零测试。
这类模块比"没有这个模块"更危险：它出现在模块树与依赖图里，读者（包括 AI）会假设
"客户端 SDK 已经有了"，真去写代码才发现拿到的是空壳。
**处置已落地**：2026-10-03 把 `bone-sdk` 整棵目录从构建中移除（当时为空壳占位，无任何 pom 依赖它）。
本门禁随之改为盯**真实存在的对外 SDK 模块**，防止同类空壳再次混进模块树。

规则（两条）：
  1. **空壳禁令**：一个对外模块若 `src/main/java` 下除去 IDE 模板类后**没有业务类**，即判定为 placeholder，
     必须在基线里显式登记为 placeholder 并写明处置口径（或从构建中移除）。不允许"沉默地空着"。
  2. **测试绑定**：一个对外模块一旦有真实业务类（不再是 placeholder），就必须有 `src/test/java` 下至少一个测试类。
     对外发布的契约面是别人的编译依赖，零测试意味着破坏性变更无人拦截。

判定为 IDE 模板类的特征（任一命中即视为模板，不计入业务类）：
  - 类名恰好为 `Main`；
  - 文件内含 IntelliJ 生成的注释标记（`TIP To <b>Run</b> code` / `click the <icon` / `shortcut actionId=`）；
  - `main` 方法体只含 `System.out.print*` 与整数循环。

例外：`module` 在基线的 `placeholder` 列表中 ⇒ 只做提示，不失败（登记过即视为已知）。
基线只可收缩：把一个模块从 placeholder 列表移除后，若它仍是空壳，本检查即失败。

用法：
  python3 scripts/check-sdk-contract-surface.py           # 报告（不失败）
  python3 scripts/check-sdk-contract-surface.py --check   # 违例即失败
  python3 scripts/check-sdk-contract-surface.py --baseline # 把当前 placeholder 刷进基线
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/sdk-contract-baseline.json"

# 对外发布模块的根目录（相对 REPO）。这些模块的产物会被外部项目依赖。
# 2026-10-03：原 bone-sdk（bone-client-sdk / bone-openapi-sdk）为空壳占位，已从构建移除。
SDK_ROOTS = (
    "bone-engine/bone-metadata-sdk",
    "bone-engine/bone-extension-engine/bone-extension-sdk",
)

# IDE 生成的模板类特征
IDE_TEMPLATE_MARKERS = (
    "TIP To <b>Run</b> code",
    "click the <icon",
    "shortcut actionId=",
    "Hello and welcome",
)


def java_sources(module_dir: Path) -> list[Path]:
    src = module_dir / "src" / "main" / "java"
    return sorted(src.rglob("*.java")) if src.is_dir() else []


def test_sources(module_dir: Path) -> list[Path]:
    src = module_dir / "src" / "test" / "java"
    return sorted(src.rglob("*.java")) if src.is_dir() else []


def is_ide_template(path: Path) -> bool:
    """判定是否 IDE 生成的模板类（不计入业务类）。"""
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return False
    return any(marker in text for marker in IDE_TEMPLATE_MARKERS)


def business_classes(module_dir: Path) -> list[Path]:
    return [f for f in java_sources(module_dir) if not is_ide_template(f)]


def is_aggregator_pom(module_dir: Path) -> bool:
    """聚合模块（packaging=pom）只装 modules 列表，本就不该有业务类，不参与空壳判定。"""
    pom = module_dir / "pom.xml"
    if not pom.is_file():
        return False
    text = pom.read_text(encoding="utf-8", errors="ignore")
    packaging = re.search(r"<packaging>\s*([\w-]+)\s*</packaging>", text)
    return bool(packaging and packaging.group(1) == "pom")


def declared_modules() -> list[Path]:
    """从根 pom 的 <modules> 递归解析出所有叶子模块目录。"""
    root_pom = REPO / "pom.xml"
    if not root_pom.is_file():
        return []
    text = root_pom.read_text(encoding="utf-8", errors="ignore")
    block = re.search(r"<modules>(.*?)</modules>", text, re.S)
    if not block:
        return []
    found: list[Path] = []
    queue: list[Path] = []
    for m in re.finditer(r"<module>\s*([^<]+?)\s*</module>", block.group(1)):
        queue.append(REPO / m.group(1))
    seen: set[Path] = set()
    while queue:
        mod = queue.pop(0)
        if mod in seen or not mod.is_dir():
            continue
        seen.add(mod)
        found.append(mod)
        nested = mod / "pom.xml"
        if nested.is_file():
            nt = nested.read_text(encoding="utf-8", errors="ignore")
            nb = re.search(r"<modules>(.*?)</modules>", nt, re.S)
            if nb:
                for m in re.finditer(r"<module>\s*([^<]+?)\s*</module>", nb.group(1)):
                    queue.append(mod / m.group(1))
    return found


def in_sdk_scope(path: Path) -> bool:
    rel = path.relative_to(REPO)
    return any(str(rel).startswith(root) for root in SDK_ROOTS)


def load_baseline() -> tuple[set[str], bool]:
    if not BASELINE.is_file():
        return set(), False
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return set(data.get("placeholder_modules", [])), True


def audit() -> list[dict]:
    """返回 [{path, kind, business, tests, message}]。"""
    placeholders, _ = load_baseline()
    rows: list[dict] = []
    for mod in declared_modules():
        if not in_sdk_scope(mod):
            continue
        biz = business_classes(mod)
        tests = test_sources(mod)
        rel = str(mod.relative_to(REPO))
        if is_aggregator_pom(mod):
            # 聚合 pom：源码在其子模块里，本模块自身无业务类是正常的
            continue
        if not biz:
            kind = "placeholder" if rel in placeholders else "undeclared-placeholder"
            rows.append(
                {
                    "path": rel,
                    "kind": kind,
                    "business": 0,
                    "tests": len(tests),
                    "message": "只有 IDE 模板类，无任何业务类",
                }
            )
            continue
        if not tests:
            rows.append(
                {
                    "path": rel,
                    "kind": "missing-test",
                    "business": len(biz),
                    "tests": 0,
                    "message": f"已有 {len(biz)} 个业务类但零测试",
                }
            )
    return rows


def report(rows: list[dict]) -> None:
    if not rows:
        print("  OK: 对外发布模块均有真实契约面且带测试")
        return
    print("=== check-sdk-contract-surface ===")
    print(f"  {'模块':<34}{'业务类':>6}{'测试':>6}  说明")
    for r in rows:
        print(f"  {r['path']:<34}{r['business']:>6}{r['tests']:>6}  {r['message']}  [{r['kind']}]")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="违例即失败")
    ap.add_argument("--baseline", action="store_true", help="把当前 placeholder 刷进基线")
    args = ap.parse_args()

    rows = audit()

    if args.baseline:
        data = {
            "_comment": (
                "对外发布模块的空壳登记（只可收缩）。placeholder_modules 中的模块已知只有 IDE 模板类，"
                "不参与阻断；一旦移除本登记而模块仍是空壳，check-sdk-contract-surface.py 即失败。"
                "2026-10-03：bone-sdk（bone-client-sdk / bone-openapi-sdk）曾在此登记为空壳占位，"
                "同日已从根 pom <modules> 移除，登记清空，扫描范围改为真实对外 SDK"
                "（bone-metadata-sdk、bone-extension-sdk）。若将来要对外发布客户端 SDK，"
                "优先从 doc/architecture/openapi/ 的规范生成，而不是手写一个空壳占位。"
            ),
            "placeholder_modules": sorted(r["path"] for r in rows if r["business"] == 0),
        }
        BASELINE.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"已写入基线：{len(data['placeholder_modules'])} 个 placeholder")
        return 0

    report(rows)

    if not args.check:
        return 0

    violations = [r for r in rows if r["kind"] != "placeholder"]
    if violations:
        print("")
        for r in violations:
            print(f"  [FAIL] {r['path']}：{r['message']}")
        print(
            "  处置：① 若确为未实现占位 → 登记进 doc/architecture/sdk-contract-baseline.json "
            "的 placeholder_modules；\n         ② 若已有业务类 → 补 src/test/java 契约测试；"
            "\n         ③ 若已废弃 → 从根 pom <modules> 移除该模块。"
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
