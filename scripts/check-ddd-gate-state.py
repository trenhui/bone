#!/usr/bin/env python3
"""Bone DDD 门禁状态 ↔ 事实真源 一致性校验。

为什么需要它：2026-09-17 复核发现 `Bone-DDD-最终实践方案.md` 的 HC 硬约束表 8 条里有 6 条与实现
不符——HC-005 写"≥70%"而 `bone-parent/pom.xml` 实际是 0.10；HC-003 / HC-006 引用的 ArchUnit
规则在 `BoneDddArchRules` 里根本不存在；HC-004 / HC-008 指名的工具没有任何工作流调用；同时
`G-3` 编号被两个章节同时占用。仓库已有的两道自查
（`scripts/check-ddd-doc-drift.py`、`scripts/ci/check-ddd-doc-code-sync.py --strict`）验的是编号
残留、符号真实性、链接与锚点可达——**都覆盖不到"文档声明的门禁状态是否等于真实状态"**，
所以另立本脚本补这一层。

十项检查：
1. 章节编号唯一：`P|E|G` + 数字 的编号不得在两个标题上重复出现；
2. 稳定锚点契约可解析：`稳定锚点与索引` 表内的每个 `#anchor` 必须落到标题自动锚或显式 `<a id>`；
3. 覆盖率阈值一致：`HC-005` 行**第一个**百分数即"声明的实测门槛"，必须等于 pom 的
   `jacoco.minimum.coverage`（×100）。因此该行写法必须无歧义，例如"实测门槛为 10% 指令覆盖率"；
4. 规则名真实性：正文引用的驼峰式 ArchUnit 规则名必须存在于 `BoneDddArchRules`；确需引用
   尚未实现的规则名时，登记到 `KNOWN_MISSING` 并写明原因；
5. 本地脚本不等于 CI 门禁：凡声明 `Active` / 已阻断的行，其点名的 `scripts/...` 脚本必须存在、
   且名字确实出现在某个 `.github/workflows/*.yml` 里——否则只是本地脚本，不能称 CI 阻断；
6. HC 表点名的脚本必须真实存在（防止把不存在的载体当门禁）；
7. **本地载体反查**：本地脚本里已真实落地的拦截（如 `scripts/check.sh` 的 ORM import 扫描），
   其对应 HC 条目**不得**标成 `Planned`（= 无实现）——2026-09-17 实测 HC-001 曾如此，
   把"已有 pre-commit 拦截"读成"完全没有"，方向与 5 相反；
8. **门禁强度差异**：父 POM 之外若有模块下调 `jacoco.minimum.coverage`，HC-005 行必须写明
   「模块覆盖」，否则读者会把默认门槛当成全模块生效；
9. `--metrics`：输出 G-1.8 定义的实施状态指标（Handler / ApplicationService / UseCase /
   `domain/repository` 计数），口径取自 `git ls-files`，数字不入库。
10. **状态真源版本一致**：`gate-state.json` 的 `version` 必须等于正文头部声明的版本号——
    否则「唯一真源」会无声落后于它描述的条文版本（2026-09-19 实测：JSON 4 项全绿却停在 5.5.9）。

另有一项**不阻断**的输出（AGENTS.md 属 §12.3 的 L4，本脚本只提示、不判失败）：

- AGENTS.md 单真源收敛提示：AGENTS.md 不应再并列维护整张 HC 表（应只留指向
  `#hc-hard-constraints` 的薄引用），也不应把无载体的 HC 写成 ArchUnit 的执行载体。

用法：python3 scripts/check-ddd-gate-state.py            （退出码非 0 即失败）
     python3 scripts/check-ddd-gate-state.py --metrics  （只打印实施状态指标）

接线状态：本脚本已由 `.github/workflows/ci.yml` 的 `backend-quality` job 执行（L3 变更，
2026-09-17 架构师确认）。`scripts/check.sh`（pre-commit）**未**接入它——熔断器按脚本计失败
次数，不宜在未验证前扩大 pre-commit 的失败面。

检查能力的边界（不要被"九项全绿"误导）：6/7 是登记式反查，只能核对**已登记**的载体；
"文档称某脚本是某条 HC 的载体，而该脚本其实不做这件事"（HC-008 曾经如此：声明的载体只比对
表名、不校验必备列）无法通用自动判定——新增 HC 载体时必须同步登记到 `LOCAL_INTERCEPT_PROBES`
或人工复核该条目。
"""

import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

DOC = REPO / "doc/architecture/Bone-DDD-最终实践方案.md"
AGENTS = REPO / "AGENTS.md"
RULES_SRC = (
    REPO
    / "bone-framework/bone-architecture-test/src/main/java/com/bone/architecture/BoneDddArchRules.java"
)
POM = REPO / "bone-parent/pom.xml"

ANCHOR_SECTION = "稳定锚点与索引"

# 4) 已确知"文档引用但共享规则库中不存在"的规则名，必须逐条登记原因。
KNOWN_MISSING = {
    "controllerMustReturnApiResponse": "HC-003 无机器载体，G-1.7 标注 Planned",
    "repositoryMustUseSdk": "HC-006 本地脚本 check-sdk-persistence.py 已落地（Manual）；ArchUnit 规则仍未实现，故保留登记",
    "domainCoreShouldOnlyDependOnAllowedPackages": (
        "bone-iam 模块级 ArchUnit 规则（不在共享规则库）；5.5.16 版本行引用它解释 "
        "RoleHierarchyResolver 为何不进 domain/service（domain 不得依赖 Spring stereotype）。"
        "提升到 BoneDddArchRules 前需先在 8 个应用模块试跑，故先登记为待提升"
    ),
}
# ADR-0030 门禁①③④⑥ 曾在此登记为"待 P3 落地的 ArchUnit 规则名"。2026-09-19 收官后，
# 它们的真实载体是 blueprint 的模块级治理测试 `SqlTemplateGovernanceTest`（不是 ArchUnit 规则），
# 因此 G-1.6 直接写该测试类名、不再引用虚构规则名，对应登记项随之删除。
# 门禁⑤ 由 ArchUnit `oneAggregatePerTransaction` 承担（改造：default / @Sql 方法按读侧排除）。

# 4) 触发检查的后缀片段：ArchUnit 规则名基本以这些谓词为中缀。
RULE_NAME = re.compile(r"\b((?:no[A-Z]\w+)|(?:[a-z]\w*(?:Must|Should|Can|Only)[A-Z]\w*))\b")
# 引用 KNOWN_MISSING 规则名的行必须带下列任一标记，证明它自己说明了"没有机器载体"。
UNIMPLEMENTED_MARK = re.compile(r"无实现|未实现|不在共享规则库|不存在|待实现|Planned")
RULE_DECL = re.compile(r"public\s+static\s+ArchRule\s+(\w+)\s*\(")
HEADING = re.compile(r"^(#{2,6})\s+(.*)$")
NUMBERED = re.compile(r"^(P|E|G)-(\d+(?:\.\d+)*)\b")
EXPLICIT_ID = re.compile(r'<a\s+id="([^"]+)"')
TABLE_ANCHOR = re.compile(r"`#([^`]+)`")
SCRIPT_REF = re.compile(r"`(?:\./)?(scripts/[\w\-./]+)`")
# 6) HC 表行（G-1.7）与 7) 本地拦截探针。
HC_TABLE_ROW = re.compile(r"^\|\s*\*{0,2}HC-0\d{2}\*{0,2}\s*\|")
ORM_PROBE = re.compile(r"mybatis|hibernate|jakarta\.persistence|com\.baomidou", re.IGNORECASE)
LOCAL_INTERCEPT_PROBES = [
    # (HC 编号, 载体路径, 能力探针)：探针命中 ⇒ 该 HC 行不得标 Planned。
    # 新增本地拦截时在此登记——"脚本里的某个 grep 对应哪条 HC"无法通用自动对齐。
    ("HC-001", "scripts/check.sh", ORM_PROBE),
    ("HC-001", "scripts/ci-check.sh", ORM_PROBE),
    ("HC-006", "scripts/check-sdk-persistence.py", re.compile(r"JdbcTemplate")),
    ("HC-006", "scripts/check.sh", re.compile(r"check-sdk-persistence")),
    ("HC-006", "scripts/ci-check.sh", re.compile(r"check-sdk-persistence")),
]
# 8) 覆盖率门禁强度：父 POM 之外的模块下调阈值时必须写进 HC-005 行。
JACOCO_PROP = re.compile(r"<jacoco\.minimum\.coverage>([\d.]+)</jacoco\.minimum\.coverage>")
# 10) 状态真源版本：gate-state.json 的 version 必须等于正文头部声明的版本号。
DOC_VERSION = re.compile(r"^>\s*\*\*版本\*\*：\s*(\d+\.\d+\.\d+)")
COVERAGE_ROOTS = (
    "bone-parent",
    "bone-framework",
    "bone-engine",
    "bone-platform",
    "bone-blueprint",
    "bone-sdk",
    "bone-tool",
)

# 6) AGENTS.md 侧仍持有 HC 状态副本的行（HC 表行）与"无载体却称 ArchUnit 载体"的漂移。
AGENTS_HC_ROW = re.compile(r"^\|\s*\*{0,2}HC-0\d{2}\*{0,2}\s*\|")
AGENTS_STALE_CARRIER = re.compile(r"HC-002\s*/\s*003\s*/\s*006|HC-002/003/006")

# 门禁状态表单真源：从 gate-state.json 渲染进文档的注入标记之间（#13 / #16）。
GATE_STATE = REPO / "doc/architecture/gate-state.json"
GENERATED_BANNER = (
    "> ⚠️ 本表由 `doc/architecture/gate-state.json` 自动生成（门禁状态唯一真源），"
    "请勿手改；改状态请改该 JSON 后运行 `python3 scripts/check-ddd-gate-state.py generate`。"
)


def render_gate_table(spec):
    """由 gate-state.json 的某张表 spec 渲染 Markdown 表。"""
    header = "| " + " | ".join(spec["header"]) + " |"
    sep = spec["sep"]
    body = "\n".join("| " + " | ".join(r) + " |" for r in spec["rows"])
    return "\n".join([header, sep, body])


def generate_gate_tables(check_only):
    """把 gate-state.json 渲染进文档的注入标记之间，实现单真源生成（#13）与渲染分离（#16）。

    check_only=True：只比对文档当前内容与应生成内容，不一致则失败（CI 用，防手工改表漂移）；
    False：就地重写文档的标记区域。
    """
    if not GATE_STATE.exists():
        print("未找到门禁状态真源：{}".format(GATE_STATE), file=sys.stderr)
        return 1
    data = json.loads(GATE_STATE.read_text(encoding="utf-8"))
    text = DOC.read_text(encoding="utf-8")
    new_text = text
    missing = []
    for key, spec in data["tables"].items():
        start = "<!-- gate-state:{}:start -->".format(key)
        end = "<!-- gate-state:{}:end -->".format(key)
        if start not in new_text or end not in new_text:
            missing.append(key)
            continue
        block = GENERATED_BANNER + "\n\n" + render_gate_table(spec)
        new_text = re.sub(
            r"{}[\s\S]*?{}".format(re.escape(start), re.escape(end)),
            start + "\n" + block + "\n" + end,
            new_text,
            count=1,
        )
    if missing:
        print(
            "文档缺少注入标记（需先加 <!-- gate-state:KEY:start/end --> 包围对应表）：{}".format(
                "、".join(missing)
            )
        )
        return 1
    if new_text == text:
        print(
            "OK: 文档门禁状态表与 gate-state.json 一致（{} 张表，无漂移）".format(
                len(data["tables"])
            )
        )
        return 0
    if check_only:
        print(
            "文档门禁状态表与 gate-state.json 不一致，请运行 "
            "`python3 scripts/check-ddd-gate-state.py generate` 重新生成"
        )
        return 1
    DOC.write_text(new_text, encoding="utf-8")
    print("OK: 已按 gate-state.json 重新生成 {} 张门禁状态表".format(len(data["tables"])))
    return 0


def gh_anchor(heading):
    """按 GitHub 规则生成标题锚点（与 check-ddd-doc-drift.py 保持一致）。"""
    a = heading.strip().lower().replace(" ", "-")
    return re.sub(r"[^\w\u4e00-\u9fff\-]", "", a)


def collect_anchors(text):
    targets = set()
    for line in text.splitlines():
        m = HEADING.match(line)
        if m:
            targets.add(gh_anchor(m.group(2)))
        for a in EXPLICIT_ID.findall(line):
            targets.add(a.lower())
    return targets


def check_unique_numbering(lines):
    errors = []
    seen = {}
    for i, line in enumerate(lines, 1):
        m = HEADING.match(line)
        if not m or "Legacy" in m.group(2):
            continue
        n = NUMBERED.match(m.group(2))
        if not n:
            continue
        key = "{}-{}".format(n.group(1), n.group(2))
        seen.setdefault(key, []).append((i, m.group(2).strip()))
    for key, hits in sorted(seen.items()):
        if len(hits) > 1:
            where = "；".join("第 {} 行「{}」".format(no, title) for no, title in hits)
            errors.append("章节编号重复 {}：{}".format(key, where))
    return errors


def check_anchor_contract(lines):
    errors = []
    targets = collect_anchors("\n".join(lines))
    inside = False
    for i, line in enumerate(lines, 1):
        m = HEADING.match(line)
        if m:
            inside = ANCHOR_SECTION in m.group(2)
            continue
        if not inside or not line.strip().startswith("|"):
            continue
        first_cell = line.strip().strip("|").split("|")[0]
        for anchor in TABLE_ANCHOR.findall(first_cell):
            if anchor.lower() not in targets:
                errors.append(
                    "第 {} 行：稳定锚点契约 `#{}` 无法解析（无对应标题或显式 <a id>）".format(i, anchor)
                )
    return errors


def check_coverage_threshold(lines):
    errors = []
    if not POM.exists():
        return ["pom 不存在，无法核对覆盖率阈值：{}".format(POM)]
    m = re.search(r"<jacoco\.minimum\.coverage>([\d.]+)</jacoco\.minimum\.coverage>", POM.read_text(encoding="utf-8"))
    if not m:
        return ["pom 未声明 jacoco.minimum.coverage，无法核对 HC-005"]
    actual = round(float(m.group(1)) * 100, 2)
    # 只认 HC 表格行作为"声明处"：版本行、叙述句里提到 HC-005 不算声明门槛。
    for i, line in hc_rows(lines):
        if "**HC-005**" not in line:
            continue
        pcts = [round(float(p), 2) for p in re.findall(r"([\d.]+)\s*%", line)]
        if not pcts:
            errors.append("第 {} 行：HC-005 未声明实测门槛百分数".format(i))
        elif pcts[0] != actual:
            errors.append(
                "第 {} 行：HC-005 声明门槛 {}% ≠ pom 实测 {}%（行内第一个百分数即声明值，请写成无歧义表述）".format(
                    i, pcts[0], actual
                )
            )
        return errors
    return ["全文 HC 表中未找到 HC-005 行，G-1.7 表可能被删除"]


def check_local_script_carriers(lines):
    """声明 Active / 已阻断的行，不能只靠本地脚本当载体。"""
    errors = []
    wf_dir = REPO / ".github/workflows"
    wf_text = "\n".join(
        p.read_text(encoding="utf-8", errors="ignore") for p in sorted(wf_dir.glob("*.yml"))
    )
    for i, line in enumerate(lines, 1):
        # 门禁状态声明只出现在表格行里；正文叙述（含"修正前的错标是……"这类复盘）不参与判定。
        if not line.strip().startswith("|"):
            continue
        if "Active" not in line and "✅" not in line:
            continue
        for path in SCRIPT_REF.findall(line):
            script = REPO / path
            if not script.exists():
                errors.append("第 {} 行：声明 Active / 已阻断，但点名的脚本不存在：{}".format(i, path))
            elif script.name not in wf_text:
                errors.append(
                    "第 {} 行：声明 Active / 已阻断，但 {} 未被任何 .github/workflows 调用，"
                    "只能算 Manual——不得称 CI 阻断".format(i, path)
                )
    return errors


def hc_rows(lines):
    """G-1.7 HC 表里的 (行号, 行内容)。"""
    return [(i, line) for i, line in enumerate(lines, 1) if HC_TABLE_ROW.match(line)]


def check_hc_script_carriers_exist(lines):
    """6) HC 表点名的 `scripts/...` 必须真实存在。"""
    errors = []
    for i, line in hc_rows(lines):
        for path in SCRIPT_REF.findall(line):
            if not (REPO / path).exists():
                errors.append("第 {} 行：HC 表点名的脚本不存在：{}".format(i, path))
    return errors


def check_local_intercept_vs_status(lines):
    """7) 本地脚本已实现的拦截，不得在 HC 表里写成 Planned（= 无实现）。

    与 5) 方向相反：5) 禁止"只有本地脚本却称 CI 阻断"，本条禁止"已有本地拦截却称无实现"。
    两条合起来才使"Manual"这个中间态稳定——多写了会被 5) 抓，少写了会被本条抓。
    """
    errors = []
    carriers_by_hc = {}
    for hc_id, rel, probe in LOCAL_INTERCEPT_PROBES:
        f = REPO / rel
        if not f.exists():
            continue
        if probe.search(f.read_text(encoding="utf-8", errors="ignore")):
            carriers_by_hc.setdefault(hc_id, []).append(rel)
    for hc_id, carriers in sorted(carriers_by_hc.items()):
        for i, line in hc_rows(lines):
            if "**{}**".format(hc_id) not in line:
                continue
            if "Planned" in line:
                errors.append(
                    "第 {} 行：{} 标为 Planned（无实现），但 {} 已落地该拦截——"
                    "本地载体存在时不得写成 Planned，应标 Manual 并点名载体".format(
                        i, hc_id, "、".join(sorted(carriers))
                    )
                )
            break
    return errors


def check_coverage_overrides(lines):
    """8) 父 POM 之外下调覆盖率的模块，必须在 HC-005 行写明。"""
    if not POM.exists():
        return []
    m = JACOCO_PROP.search(POM.read_text(encoding="utf-8"))
    if not m:
        return []
    default = float(m.group(1))
    overrides = []
    for root in COVERAGE_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for pom in sorted(base.glob("**/pom.xml")):
            if "target" in pom.parts or "node_modules" in pom.parts:
                continue
            mm = JACOCO_PROP.search(pom.read_text(encoding="utf-8", errors="ignore"))
            if mm and float(mm.group(1)) < default:
                overrides.append(
                    "{}={}".format(pom.parent.relative_to(REPO).as_posix(), mm.group(1))
                )
    if not overrides:
        return []
    for i, line in enumerate(lines, 1):
        if "**HC-005**" not in line:
            continue
        if "模块覆盖" in line:
            return []
        return [
            "第 {} 行：父 POM 门槛 {} 之外有 {} 个模块下调了 jacoco.minimum.coverage（{}）——"
            "HC-005 行必须写明「模块覆盖」，否则读者会把默认门槛当成全模块生效".format(
                i, default, len(overrides), "、".join(sorted(overrides))
            )
        ]
    return []


def check_rule_names(lines):
    errors = []
    if not RULES_SRC.exists():
        return ["ArchUnit 规则库不存在，无法核对规则名：{}".format(RULES_SRC)]
    declared = set(RULE_DECL.findall(RULES_SRC.read_text(encoding="utf-8")))
    reported = set()
    for i, line in enumerate(lines, 1):
        for name in RULE_NAME.findall(line):
            if name in declared:
                continue
            if name in KNOWN_MISSING:
                # 登记为"已知不存在"的规则名，引用它的那一行必须自证未实现，
                # 否则读者会把缺失的载体当成已有门禁（这正是 HC-003/HC-006 的病根）。
                if not UNIMPLEMENTED_MARK.search(line):
                    errors.append(
                        "第 {} 行：规则名 `{}` 已登记为不存在的载体（{}），但该行未标注"
                        "「无实现 / Planned」".format(i, name, KNOWN_MISSING[name])
                    )
                continue
            if name in reported:
                continue
            reported.add(name)
            errors.append(
                "第 {} 行：规则名 `{}` 不在 BoneDddArchRules 中（若确为未实现的规则名，"
                "请登记到本脚本 KNOWN_MISSING 并在正文标注 Planned）".format(i, name)
            )
    return errors


def check_agents_single_source():
    """6) AGENTS.md 是否仍并列维护 HC 状态副本（提示项，不阻断）。

    2026-09-17：AGENTS.md 已拆分（薄引用入口 + `doc/agents/` 六份正文），其 §12.1 收敛为
    指向 `#hc-hard-constraints` 的薄引用，本项随之归零。保留它的目的是**防回归**——
    一旦有人把 HC 表抄回入口或 `doc/agents/`，提示会重新出现。

    不阻断的理由：收敛 AGENTS.md §12.1 属该文件 §12.3 的 L4（完全禁止 AI 执行），
    只能由架构师本人授权执行；本检查的职责是把"双写仍然存在"变成每次可复现的输出，
    而不是让一个 AI 无权修的问题把整条检查判红。
    """
    warnings = []
    if not AGENTS.exists():
        return warnings
    lines = AGENTS.read_text(encoding="utf-8").splitlines()
    rows = [i for i, line in enumerate(lines, 1) if AGENTS_HC_ROW.match(line)]
    if rows:
        warnings.append(
            "AGENTS.md 仍持有 {} 行 HC 状态副本（首次出现于第 {} 行）——状态真源是 G-1.7，"
            "该处应收敛为指向 `#hc-hard-constraints` 的薄引用（§12.3 L4，需架构师执行）".format(
                len(rows), rows[0]
            )
        )
    for i, line in enumerate(lines, 1):
        if AGENTS_STALE_CARRIER.search(line):
            warnings.append(
                "AGENTS.md 第 {} 行把 ArchUnit `*ArchitectureTest` 称作 HC-002/003/006 的执行载体，"
                "但 HC-003 / HC-006 无共享规则、无机械载体（G-1.7 标 Planned）".format(i)
            )
    return warnings


def check_version_alignment(lines):
    """10) gate-state.json 的 version 必须等于正文头部声明的版本号。

    没有这条时，状态真源会在无声中落后于它要描述的那一版条文——2026-09-19 实测即
    `gate-state.json=5.5.9` 而正文已是 `5.5.10`，而当时九项检查全绿。
    """
    doc_ver = None
    for ln in lines[:20]:
        m = DOC_VERSION.match(ln.strip())
        if m:
            doc_ver = m.group(1)
            break
    if doc_ver is None:
        return ["未能在本文头部解析出「> **版本**：x.y.z」版本行，无法核对状态真源版本"]
    try:
        state = json.loads(GATE_STATE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return ["读取门禁状态真源失败：{}".format(exc)]
    state_ver = str(state.get("version", ""))
    if state_ver != doc_ver:
        return [
            "gate-state.json 的 version={} 与本文版本 {} 不一致——状态真源落后于它描述的条文版本；"
            "须同批更新（改 JSON 后运行 `python3 scripts/check-ddd-gate-state.py generate`）".format(
                state_ver or "(缺失)", doc_ver
            )
        ]
    return []


METRIC_SUFFIXES = {
    "*CommandHandler 类数": "CommandHandler.java",
    "*ApplicationService 类数": "ApplicationService.java",
    "*QueryHandler 类数": "QueryHandler.java",
    "*UseCase 类数": "UseCase.java",
}


def collect_metrics():
    """7) G-1.8 实施状态指标，返回 (索引口径, 工作树口径, 错误信息)。

    两个口径都给，是因为重构期间只看一个会骗人：索引口径**漏掉未提交新增**（blueprint 内联
    Handler 时新建的 `*ApplicationService` 还没 add 就不计），同时**保留已在工作树删除的文件**
    （2026-09-19 实测：`OrderPaidConsumptionApplicationService` 已删、索引里仍在，`*ApplicationService`
    因此长期报 3）。索引口径是"已提交现状"，工作树口径是"此刻磁盘现状"，二者差集即为在途改动。
    """
    proc = subprocess.run(
        ["git", "ls-files"], cwd=str(REPO), capture_output=True, text=True
    )
    if proc.returncode != 0:
        return None, None, "git ls-files 失败：{}".format(proc.stderr.strip())
    # 只计 src/main/java：测试夹具里刻意造的 *CommandHandler / *UseCase 不算产品代码
    # （bone-architecture-test 的 fixture 会污染计数，实测 UseCase 类数因此从 1 变 0）。
    tracked = {
        f for f in proc.stdout.splitlines() if f.endswith(".java") and "/src/main/java/" in f
    }
    on_disk = {f for f in tracked if (REPO / f).exists()}
    # 工作树口径：tracked 中仍存在 + 未跟踪新增（--others 覆盖新增，--exclude-standard 尊重 .gitignore）
    others = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard"],
        cwd=str(REPO),
        capture_output=True,
        text=True,
    )
    if others.returncode == 0:
        on_disk |= {
            f
            for f in others.stdout.splitlines()
            if f.endswith(".java") and "/src/main/java/" in f
        }

    def snapshot(files):
        out = {
            name: sum(1 for f in files if Path(f).name.endswith(suffix))
            for name, suffix in METRIC_SUFFIXES.items()
        }
        out["domain/repository 接口数"] = sum(1 for f in files if "/domain/repository/" in f)
        return out

    return snapshot(tracked), snapshot(on_disk), None


def main():
    args = sys.argv[1:]
    if "--metrics" in args:
        tracked, on_disk, err = collect_metrics()
        if err:
            print(err, file=sys.stderr)
            return 1
        print("Bone DDD 实施状态指标（口径见 G-1.8；数字不入库，每次现算）")
        print("  {:<26} {:>6} {:>10} {:>9}".format("指标", "索引", "工作树", "差值"))
        for name in tracked:
            a, b = tracked[name], on_disk[name]
            print("  {:<26} {:>6} {:>10} {:>+9}".format(name, a, b, b - a))
        print(
            "  {:<26} {}".format(
                "统计范围", "src/main/java 的 .java（不含测试夹具）；索引口径=已提交现状，工作树口径=此刻磁盘"
            )
        )
        if tracked != on_disk:
            print(
                "  {:<26} {}".format(
                    "差集含义", "在途改动（未提交新增 / 已删除未提交），两边不一致时以工作树口径读迁移进度"
                )
            )
        return 0

    if args and args[0] == "generate":
        return generate_gate_tables(check_only="--check" in args)

    if not DOC.exists():
        print("未找到 DDD 规范文档：{}".format(DOC), file=sys.stderr)
        return 1
    lines = DOC.read_text(encoding="utf-8").splitlines()

    errors = []
    errors += check_unique_numbering(lines)
    errors += check_anchor_contract(lines)
    errors += check_coverage_threshold(lines)
    errors += check_rule_names(lines)
    errors += check_local_script_carriers(lines)
    errors += check_hc_script_carriers_exist(lines)
    errors += check_local_intercept_vs_status(lines)
    errors += check_coverage_overrides(lines)
    errors += check_version_alignment(lines)

    warnings = check_agents_single_source()

    if warnings:
        print("提示（不阻断，收敛需架构师执行）：")
        for w in warnings:
            print("  ! {}".format(w))

    if errors:
        print("DDD 门禁状态一致性检查失败：")
        for e in errors:
            print("  - {}".format(e))
        return 1
    print(
        "OK: 门禁状态检查通过（编号唯一 / 锚点契约可解析 / 覆盖率阈值一致 / 规则名真实 / "
        "Active 载体不在本地脚本 / HC 载体存在 / 本地拦截未被写成无实现 / 覆盖率模块覆盖已声明；"
        "状态真源版本与正文一致；"
        "Known-missing 登记 {} 条；提示 {} 条）".format(len(KNOWN_MISSING), len(warnings))
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
