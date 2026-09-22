#!/usr/bin/env python3
"""application 层构件白名单检查（ADR-0035 派生门禁）。

规则真源：`doc/architecture/Bone-DDD-最终实践方案.md` E-10.2 / E-13.2 / E-3.2。

为什么需要它：application 层唯一合法形态是**用例入口 `*ApplicationService`（平铺在 `application/` 根目录）**
＋ 契约与端口（`*Command` / `*Query` / `*Dto` / `*Port` / `*Projection` / `*Assembler` 等）
＋ `support/`（只协调技术端口、不碰 domain 的技术编排）。
除此之外的"第二类 service"（`application/service/**`、`application/binding/**`、`application/policy/**`、
散落的 `*Service` 类）都是"用调用关系而不是职责划分构件"的产物：它与 `*ApplicationService` 的唯一差别
是"是否被 adapter 直接调用"，而调用者数量会随重构变化——判据不稳定，认知成本却摊到每次改动
（2026-09-22 复核：IAM 已把 `application/service/` 分化成 `service/` + `binding/` + `policy/` 三个角色包，
另有 57 个 `*ApplicationService` 被放进 `command/handler` / `query/handler`）。

三条规则（全部只做结构与命名判定，不碰语义）：
  R1 禁用角色包：application 下的顶层子包必须命中白名单；`service` / `binding` / `policy` / `common` 等
     "按调用关系划分"的包名一律违规。
  R2 唯一 service 形态：application 下不得出现以 `Service` 结尾但不是 `ApplicationService` 的类
     （`*Service` 后缀只在 `domain/service` 合法，E-13.2）。
  R3 位置：`*ApplicationService` 必须平铺在 `application/` 根目录（E-10.2），不得塞进 command/query 子包。

存量违规登记在 `doc/architecture/application-constructs-baseline.json`，只可收缩：
新增命中即失败；清掉一条后 `--check` 会提示可从基线移除。

用法：
  python3 scripts/check-application-constructs.py            # 报告（不失败）
  python3 scripts/check-application-constructs.py --check    # 新增违规即失败（ci-check.sh 调用）
  python3 scripts/check-application-constructs.py --baseline # 把当前违规刷进基线
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/application-constructs-baseline.json"

# R1：application 下允许的顶层子包（其余一律违规；新增类别须先改规范 + ADR）
ALLOWED_SUBPACKAGES = {
    "command",
    "query",
    "port",
    "event",
    "config",
    "support",
    "orchestration",
    "integration",
    "dto",
    "util",
    "idempotency",
}

# 显式点名的禁用包（除白名单外，这些名字历史上出现过，最可能复发）
BANNED_SUBPACKAGE_HINTS = {"service", "binding", "policy", "common"}

SCAN_ROOTS = ("bone-platform", "bone-engine", "bone-framework", "bone-blueprint")
SKIP_DIRS = {"target", "node_modules", ".git", "build", "dist"}
CLASS_DECL = re.compile(r"^public\s+(?:final\s+|abstract\s+)?(?:class|interface|record|enum)\s+(\w+)")
SERVICE_SUFFIX = re.compile(r"Service$")


def module_of(path: Path) -> str:
    parts = path.relative_to(REPO).parts
    return "/".join(parts[:2]) if len(parts) > 1 else parts[0]


def scan() -> list[dict]:
    """返回违规列表 [{rule, id, path, name, package, detail}]。

    R1 也按**文件**记一条（id 含路径）：只按目录记会让"已存在的角色包里新增文件"漏过门禁
    ——目录被基线冻结 ≠ 内容可以继续增长。
    """
    violations: list[dict] = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for java in base.rglob("*.java"):
            if SKIP_DIRS & set(java.parts):
                continue
            posix = java.as_posix()
            if "/src/main/java/" not in posix:
                continue
            marker = "/application/"
            if marker not in posix:
                continue
            rel = java.relative_to(REPO)
            name = java.stem
            after = posix.split(marker, 1)[1]
            sub = after.split("/", 1)[0] if "/" in after else ""
            in_root = sub == "" or sub == java.name
            top_pkg = "" if in_root else sub

            # R1：禁用角色包 / 白名单外的新顶层子包（按目录聚合）
            if top_pkg and top_pkg not in ALLOWED_SUBPACKAGES:
                hint = "，且属于历史禁用名" if top_pkg in BANNED_SUBPACKAGE_HINTS else ""
                violations.append(
                    {
                        "rule": "R1-role-package",
                        "id": "R1:{}".format(rel.as_posix()),
                        "path": rel.as_posix(),
                        "name": name,
                        "package": top_pkg,
                        "detail": "application 下不得有 '{}' 这类按调用关系划分的角色包{}；本文件需按 ADR-0035 D3 归位".format(
                            top_pkg, hint
                        ),
                    }
                )

            # R2：唯一 service 形态
            if SERVICE_SUFFIX.search(name) and not name.endswith("ApplicationService"):
                violations.append(
                    {
                        "rule": "R2-extra-service",
                        "id": "R2:{}".format(rel.as_posix()),
                        "path": rel.as_posix(),
                        "name": name,
                        "package": top_pkg or "(root)",
                        "detail": "application 下的唯一 service 形态是 *ApplicationService；*Service 只在 domain/service 合法（E-13.2）",
                    }
                )

            # R3：ApplicationService 位置
            if name.endswith("ApplicationService") and not in_root:
                violations.append(
                    {
                        "rule": "R3-application-service-placement",
                        "id": "R3:{}".format(rel.as_posix()),
                        "path": rel.as_posix(),
                        "name": name,
                        "package": top_pkg,
                        "detail": "*ApplicationService 必须平铺在 application/ 根目录（E-10.2），当前在 '{}' 子包".format(
                            top_pkg
                        ),
                    }
                )
    return sorted(violations, key=lambda v: (v["rule"], v["path"]))


def load_baseline() -> dict:
    if not BASELINE.exists():
        return {}
    return json.loads(BASELINE.read_text(encoding="utf-8")).get("violations", {})


def write_baseline(violations: list[dict]) -> None:
    entries = {}
    for v in violations:
        entries[v["id"]] = {
            "rule": v["rule"],
            "path": v["path"],
            "name": v["name"],
            "package": v["package"],
            "classification": "pending",
            "owner": module_of(REPO / v["path"]),
            "detail": v["detail"],
        }
    doc = {
        "_comment": (
            "application 层构件白名单的存量违规登记（唯一真源）。规则见 Bone-DDD-最终实践方案 E-10.2 / E-13.2 与 "
            "ADR-0035：application 只放 *ApplicationService（根目录）+ 契约与端口 + support/。新增违规即失败；"
            "本表内条目只可收缩（清掉后 --check 会提示移除）。classification：pending（待迁移，属代码侧工作）。"
        ),
        "_rules": [
            "R1-role-package：application 顶层子包必须在白名单内（禁用 service / binding / policy / common 等角色包；**按文件**记，防止已冻结的角色包继续长文件）",
            "R2-extra-service：application 下不得有 *Service（唯一形态是 *ApplicationService）",
            "R3-application-service-placement：*ApplicationService 必须平铺在 application/ 根目录",
        ],
        "_migration": {
            "_note": "三类违规的清除动作不同，建议按 R3 → R2 → R1 的顺序（先机械移动、再定性、最后拆角色包），全程触达即收敛，不搞一次性大改（ADR-0032：批量须走受控通道）。",
            "R3-application-service-placement": "纯位置调整：把 command/handler 与 query/handler 下的 *ApplicationService 平铺到 application/ 根目录并同步调用方 import（E-10.2）。零语义风险。",
            "R2-extra-service": "逐个定性后再动：规则类 → 聚合或 domain/service（异常语义须改成 DomainException 并由应用层翻译，E-5.3.1）；读侧统计 → QueryPort 或域仓储 default 读方法（ADR-0030）；技术能力（缓存失效 / 审计落库 / 幂等）→ application/port/out + infrastructure；用例编排 → 并进同义 *ApplicationService（E-3.8）。零调用方的直接删除。",
            "R1-role-package": "文件级动作：每个文件按 ADR-0035 D3 的四个落点归位；文件清空后删除角色包目录（不要为保目录留占位类）。integration 的 application/service 里混着端口（FlowRuntime）与实现（LinearSyncFlowRuntime / CamelFlowRuntime / FlowNodeExecutor）：端口迁 application/port/out，实现迁 infrastructure（E-10.1）。",
            "direction-conflict": "2026-09-22 收口：bone-iam/README.md 一度把复用类定位到 application/binding/ 与 application/policy/（另有根目录 *Resolver），与 ADR-0035 白名单相反。已按用户决定（应用层只保留 *ApplicationService）统一：该 README 改为 ADR-0035 D3 的落点表，三处按存量处理；@Service 不构成留在应用层的理由——domainCoreShouldOnlyDependOnAllowedPackages 禁止 domain 依赖 Spring stereotype，正解是让领域类不带 stereotype（应用层构造或 config 里 @Bean 装配）。",
        },
        "violations": entries,
    }
    BASELINE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="出现基线外的新违规即失败")
    ap.add_argument("--baseline", action="store_true", help="把当前违规刷进基线")
    args = ap.parse_args()

    violations = scan()

    if args.baseline:
        write_baseline(violations)
        print("OK: 已写入基线 {} 条 → {}".format(len(violations), BASELINE.relative_to(REPO)))
        return 0

    by_rule: dict[str, int] = {}
    for v in violations:
        by_rule[v["rule"]] = by_rule.get(v["rule"], 0) + 1

    if not args.check:
        print("application 层构件白名单报告")
        if not violations:
            print("OK: 无违规")
            return 0
        print("  违规 {} 条：{}".format(
            len(violations), "、".join("{} {}".format(k, n) for k, n in sorted(by_rule.items()))
        ))
        baseline = load_baseline()
        for v in violations:
            mark = "baselined" if v["id"] in baseline else "NEW"
            label = v["path"] if v["rule"].startswith("R1") else v["name"]
            print("    [{}] {:<52} {}".format(mark, label, v["detail"][:96]))
        return 0

    baseline = load_baseline()
    errors = [v for v in violations if v["id"] not in baseline]
    current_ids = {v["id"] for v in violations}
    for stale in sorted(set(baseline) - current_ids):
        print("提示：`{}` 已不在违规列表中，可从基线移除".format(stale))

    if errors:
        print("application 层构件白名单检查失败：")
        for v in errors:
            print("  - {}（{}）：{}".format(v["name"], v["path"], v["detail"]))
        return 1

    print(
        "OK: application 层构件白名单检查通过（新增违规 0；基线内 {} 条存量未扩大）".format(len(baseline))
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
