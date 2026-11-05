#!/usr/bin/env python3
"""ADR-0036 D5：domain 分组布局校验。

规范真源：`doc/architecture/Bone-DDD-最终实践方案.md` E-10（唯一形态 `domain/model/{聚合}/`）
与 `doc/architecture/adr/0036-domain-model-package-single-standard.md`。

六条判定（纯目录/文件判定，不编译）：
  C1 并存      domain/{x} 与 domain/model/{x} 同时存在（违反 R4）
  C2 model 平铺 domain/model/ 下直接放 .java（聚合必须各自成包，D1）
  C3 空聚合包  domain/model/{x}/ 递归内没有任何 .java
  C4 根下可疑包 domain/{x} 不在白名单且未登记基线（疑似 R5 扁平存量）
  C5 失效豁免 基线条目指向的 domain 子包已不存在（已删/改名/移入 model）
  C6 package 一致 domain 树内 .java 的 package 声明必须与目录一致（2026-11-05 新增：
     git mv 后漏改 package 只有编译能暴露，本判据把暴露点前移到提交期——
     ClaimThresholds 移入 model/config/ 实例）

白名单：model / repository / service / gateway。模块自有的领域端口子包（如 iam、integration
的 client、blueprint 的 extension）登记在 doc/architecture/domain-model-layout-baseline.json，
只可收缩。

C5 的意义（2026-10-30 补）：基线此前只用于「是否豁免」这一个方向——目录一旦被删除或改名，
对应条目再也不会被任何代码路径看到，却照样留在文件里。这等于豁免额度只增不减，将来谁把同名
包加回来就直接静默放行。同一失效模式在 i18n 白名单棘轮、doc-code-symbols 失效判据、
HC-008 基线过宽判据上已各修一次，这是第四处。

豁免：
  - SDK / 框架模块（bone-framework/*、bone-metadata-sdk）与 Go 引擎；
  - 非四层模块——本形态是 E-10「模块内四层包结构」的一部分，只在同时具备 application 与
    adapter 包的应用模块内适用。按层拆成多个 Maven 模块的引擎（如 bone-metadata-engine 的
    -domain / -ports / -runtime / -starter）不套用，其边界由 ADR-0027 与各自的 ADR 决定。

用法：
  python3 scripts/check-domain-model-layout.py
  python3 scripts/check-domain-model-layout.py --root <dir>   # 负向探针 / 单模块校验
退出码非 0 即失败，供 scripts/ci-check.sh 消费。
"""

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/domain-model-layout-baseline.json"

PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

ALLOWED_DOMAIN_SUBPACKAGES = {"model", "repository", "service", "gateway"}
SKIP_PATH_PARTS = {"target", "node_modules", "bone-framework", "bone-metadata-sdk", "go-engine"}


def load_baseline():
    if not BASELINE.exists():
        return set()
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return {(entry["module"], entry["path"]) for entry in data.get("exceptions", [])}


def is_four_layer_module(java_dir: Path) -> bool:
    """E-10 四层骨架的适用判据：模块内同时存在 application 与 adapter 包。"""
    has_application = any(p.is_dir() for p in java_dir.rglob("application"))
    has_adapter = any(p.is_dir() for p in java_dir.rglob("adapter"))
    return has_application and has_adapter


def find_domain_dirs(root: Path):
    """返回 (模块相对路径, domain 目录) 与跳过的非四层模块列表。"""
    found = []
    skipped = []
    for java_dir in root.rglob("src/main/java"):
        if any(part in SKIP_PATH_PARTS for part in java_dir.parts):
            continue
        module = java_dir.parents[2]
        rel_module = module.relative_to(root).as_posix()
        if not is_four_layer_module(java_dir):
            if any(p.is_dir() for p in java_dir.rglob("domain")):
                skipped.append(rel_module)
            continue
        for domain_dir in java_dir.rglob("domain"):
            if not domain_dir.is_dir():
                continue
            # 只认顶层 domain 包（父目录不得再是 domain 的子包）
            if "domain" in domain_dir.parent.relative_to(java_dir).parts:
                continue
            found.append((rel_module, domain_dir))
    return sorted(found), sorted(set(skipped))


def java_files(directory: Path):
    return [p for p in directory.rglob("*.java") if "target" not in p.parts]


def _java_source_root(domain_dir: Path):
    """domain_dir 形如 .../src/main/java/<pkg>/domain；向上定位 src/main/java 根。"""
    for p in domain_dir.parents:
        if p.name == "java" and p.parent.name == "main":
            return p
    return None


def check_package_consistency(rel_module: str, domain_dir: Path) -> list:
    """C6：package 声明必须与文件所在目录一致（2026-11-05 新增）。

    背景：git mv 移动 .java 后漏改 package，只有 mvn compile 能暴露——pre-commit 的
    本门禁当时只看目录布局，形同虚设（ClaimThresholds 移入 domain/model/config/ 实例）。
    本判据把暴露点前移到门禁，提交期即红。范围限于 domain 树（本门禁的管辖面）；
    domain 之外的同型缺陷由编译期兜底。
    """
    problems = []
    java_root = _java_source_root(domain_dir)
    if java_root is None:
        return problems  # 非标准 src/main/java 布局，目录问题已由 C1–C4 覆盖
    for f in java_files(domain_dir):
        expected = ".".join(f.parent.relative_to(java_root).parts)
        m = PACKAGE_RE.search(f.read_text(encoding="utf-8", errors="ignore"))
        if m is None:
            problems.append(
                "C6 {}: {} 未声明 package（默认包）—— .java 必须声明与目录一致的 package".format(
                    rel_module, f.relative_to(java_root).as_posix()
                )
            )
        elif m.group(1) != expected:
            problems.append(
                "C6 {}: {} 的 package 声明（{}）与目录不一致（应为 {}）"
                "—— 疑似移动文件后漏改 package，编译必失败".format(
                    rel_module,
                    f.relative_to(java_root).as_posix(),
                    m.group(1),
                    expected,
                )
            )
    return problems


def check_domain(rel_module: str, domain_dir: Path, baseline) -> list:
    problems = []
    subdirs = {d.name: d for d in sorted(domain_dir.iterdir()) if d.is_dir()}
    model_dir = subdirs.get("model")

    # C2：model 下不得直接放 .java
    if model_dir is not None:
        flat = sorted(p.name for p in model_dir.glob("*.java"))
        if flat:
            problems.append(
                "C2 {}: domain/model/ 下直接放类文件 [{}] —— 聚合必须各自成包（ADR-0036 D1）".format(
                    rel_module, ", ".join(flat[:5]) + ("…" if len(flat) > 5 else "")
                )
            )
        aggregates = {d.name: d for d in sorted(model_dir.iterdir()) if d.is_dir()}
    else:
        aggregates = {}

    # C1：domain/{x} 与 domain/model/{x} 并存
    coexisting = sorted(set(subdirs) & set(aggregates))
    for name in coexisting:
        problems.append(
            "C1 {}: domain/{} 与 domain/model/{} 并存 —— 两套分组不得并存（ADR-0036 R4）".format(
                rel_module, name, name
            )
        )

    # C3：聚合包为空
    for name, agg_dir in aggregates.items():
        if not java_files(agg_dir):
            problems.append(
                "C3 {}: domain/model/{}/ 内没有任何 .java —— 空聚合包，删除或补齐".format(
                    rel_module, name
                )
            )

    # C4：domain 根下非白名单子包
    for name in sorted(subdirs):
        if name in ALLOWED_DOMAIN_SUBPACKAGES:
            continue
        if name in coexisting:
            continue  # 已由 C1 报出，避免同一目录刷两条
        if (rel_module, "domain/" + name) in baseline:
            continue
        problems.append(
            "C4 {}: domain/{} 不在白名单 [{}] 且未登记基线 —— 疑似 R5 扁平聚合；"
            "若确为领域端口子包，登记到 {}".format(
                rel_module,
                name,
                " / ".join(sorted(ALLOWED_DOMAIN_SUBPACKAGES)),
                BASELINE.relative_to(REPO),
            )
        )

    # C6：package 声明 ↔ 目录一致（git mv 后漏改 package，编译前即红）
    problems.extend(check_package_consistency(rel_module, domain_dir))
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=str(REPO), help="校验根目录（默认仓库根）")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    baseline = load_baseline()
    problems = []
    checked = 0
    domain_dirs, skipped = find_domain_dirs(root)
    for rel_module, domain_dir in domain_dirs:
        checked += 1
        problems.extend(check_domain(rel_module, domain_dir, baseline))

    # C5：基线失效判据（「只可收缩」的另一半）。
    # 登记的 (module, domain/x) 必须仍对应一个真实存在的子目录；否则该条目已无约束对象，
    # 留着只会让同名包将来被静默放行。模块本身不在扫描范围（非四层 / 已删除）时不判，
    # 避免把「模块整体被跳过」误报成「条目失效」。
    present = set()
    for rel_module, domain_dir in domain_dirs:
        if not domain_dir.is_dir():
            continue
        for child in domain_dir.iterdir():
            if child.is_dir():
                present.add((rel_module, "domain/" + child.name))
    scannable = {m for m, _ in domain_dirs}
    for module, path in sorted(baseline):
        if module not in scannable:
            continue
        if (module, path) not in present:
            problems.append(
                "C5 {}: 基线条目 {} 已失效——该 domain 子包不存在（已删除 / 改名 / 移入 model），"
                "请从基线移除".format(module, path)
            )

    if problems:
        print("domain 分组布局校验失败（ADR-0036 D5）:")
        for problem in problems:
            print("  - " + problem)
        if skipped:
            print("  （已跳过非四层模块：{}）".format("、".join(skipped)))
        return 1

    print(
        "OK: domain 分组布局校验通过（{} 个 domain 包；C1 并存 / C2 model 平铺 / C3 空聚合包 / "
        "C4 根下可疑包 / C5 失效豁免 / C6 package 一致；基线例外 {} 条；跳过非四层模块 {} 个）".format(
            checked, len(baseline), len(skipped)
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
