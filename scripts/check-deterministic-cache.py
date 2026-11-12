#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""结果缓存哨兵：确定性优先于概率性命中（D-7）。

WHY（2026-10-08）：95e4779a 摘除了元数据引擎里三类「概率性/错位」缓存，
次日 78515a39 的批量 chore 提交把它们**整批静默回退**——根因是修复只存在于
「人的记忆 + 旧工作区」里，任何一次未刷新的工作区覆盖都会无声冲掉它，
编译、单测、既有门禁全部不妨碍这次回退。

因此把「哪些缓存允许存在」从设计决策降级为机器判据，按事故 anchor 到具体文件
（而非全仓宽泛扫描，避免误伤）：

  R1 ExpressionEngine：禁「结果缓存」（expressionResultCache / EXPRESSION_CACHE_NAME）
     与 Spring Cache 通道（CacheManager / setCacheManager / org.springframework.cache.*）。
     方法引用缓存（propertyAccessorCache）与编译产物缓存（compiledExpressionCache）
     是**确定性键**（expression 字符串 → 解析产物），与输入上下文无关，允许保留。
     结果缓存为什么禁：键拼了 context.hashCode()，哈希碰撞时不同上下文串用同一份
     求值结果（实体 A 的价格算出实体 B 的价格），且该 Map 无界无 TTL ⇒ 慢性 OOM。

  R2 RuleEngine：三处参数求值必须走类型正确的入口 eval(String,Object)/
     evaluateBooleanExpression。回退版走的 evaluateExpression 返回 String⇒
     evaluateCondition 里 Boolean.TRUE.equals("true")==false ⇒ 条件恒 false，
     自定义业务规则在「接线修复把它送进来」之后恰恰在此被吞。

  R3 ValidationEngine：禁 @Cacheable。曾错挂 validateEntityByType（键 = entityType +
     Objects.hashCode(entityData)）：校验结果语义上依赖 entityData 全部内容，不可按
     hashCode 拆键；且 @EnableCaching 由宿主声明（starter 的测试入口有、metadata-server
     没有）⇒ 同一段代码在 不同部署形态下行为不同，无法推理。

  R4 MetadataEngine：禁死配置三件套 maxRetries / retryDelay / expressionEngineCache。
     全仓零消费者（曾是 chat 脚手架残留），纯误导：读代码的人以为有重试/缓存语义。

注释与 javadoc 中的历史记载（如 ValidationEngine 上还原事故的说明）不参与匹配，
杜绝「写注释解释为什么禁」反而触发门禁的自指窘境。

退出码：0=无违规；1=有违规（每个违规一行 file:line + 判据名）或锚定目录缺失。

迁移说明（2026-11-12）：已迁到 gatekit 共享框架
（scripts/lib/gatekit）。门禁只写判据本身，退出码/输出/仓库根定位/--self-test
由框架统一提供；check.sh 仅消费退出码，输出格式统一为 [FAIL]/[OK] 不影响编排。
判据逻辑、正则、相对路径格式与退出码语义均与原版逐字一致。
"""

import re
import sys
import argparse
from pathlib import Path

# --- gatekit bootstrap：把 scripts/lib 加入 sys.path 后以顶层包 import ---
# 注意 bone/ 本身是一个 Python 包，故用「scripts/lib 加 path → import gatekit」，
# 而非 import lib.gatekit（会被 bone 包名空间吞掉）。详见 scripts/lib/gatekit/core.py。
def _gatekit_path() -> str:
    p = Path(__file__).resolve().parent
    for _ in range(8):
        cand = p / "lib" / "gatekit"
        if cand.is_dir():
            return str(p / "lib")
        if p.parent == p:
            break
        p = p.parent
    raise RuntimeError("找不到 scripts/lib/gatekit")


sys.path.insert(0, _gatekit_path())
from gatekit import run, Report, repo_root  # noqa: E402

REPO = repo_root()
RUNTIME_MAIN = (
    REPO
    / "bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime"
)


def scan_file(report: Report, path: Path, rules, label: str, repo: Path) -> None:
    """对单个文件逐行跑规则。注释行（*/·//·/* 开头）跳过。

    文件不存在时视为通过：门禁锚定的是「若此文件存在则不得含 X」，
    文件被删除/移动属结构变更，由 ArchUnit 与 review 兜底，不在此误报。
    """
    if not path.exists():
        return
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if (
            stripped.startswith("*")
            or stripped.startswith("//")
            or stripped.startswith("/*")
        ):
            continue
        for pattern, name in rules:
            if re.search(pattern, line):
                report.fail(
                    f"{path.relative_to(repo)}:{lineno}: [{label}] {name} —— {stripped}"
                )


def judge(report: Report, args: argparse.Namespace,
          *, repo: Path | None = None, runtime_main: Path | None = None) -> None:
    """门禁判据本体。repo / runtime_main 可覆盖，供 --self-test 注入临时树。"""
    repo = repo or REPO
    runtime_main = runtime_main or RUNTIME_MAIN

    if not runtime_main.exists():
        report.fail(f"锚定目录不存在（结构变更请同步更新本脚本锚点）: {runtime_main}")
        return

    # R1a: ExpressionEngine 禁结果缓存与 Spring Cache 通道
    expression_engine = runtime_main / "ExpressionEngine.java"
    scan_file(
        report,
        expression_engine,
        [
            (r"expressionResultCache", "R1a 结果缓存回归（hashCode 键可串结果）"),
            (r"EXPRESSION_CACHE_NAME", "R1a 结果缓存命名常量回归"),
            (r"\bCacheManager\b", "R1b Spring Cache 通道回归（CacheManager）"),
            (r"org\.springframework\.cache\.", "R1b Spring Cache 导入回归"),
            (r"context\.hashCode\(\)", "R1c hashCode 参与缓存键（概率性命中）"),
        ],
        label="ExpressionEngine",
        repo=repo,
    )

    # R1c: runtime 整包禁 Spring Cache 通道（覆盖未来新增类的旁路）
    for java in runtime_main.rglob("*.java"):
        scan_file(
            report,
            java,
            [
                (r"import\s+org\.springframework\.cache\.", "R1c Spring Cache 导入（runtime 整包禁用）"),
            ],
            label="runtime-package",
            repo=repo,
        )

    # R2: RuleEngine 三处求值必须走 eval / evaluateBooleanExpression
    scan_file(
        report,
        runtime_main / "RuleEngine.java",
        [
            (
                r"\.evaluateExpression\s*\(",
                "R2 参数求值必须走 eval/evaluateBooleanExpression（String 布尔比较恒 false）",
            ),
        ],
        label="RuleEngine",
        repo=repo,
    )

    # R3: ValidationEngine 禁 @Cacheable（hashCode 键 + @EnableCaching 由宿主决定）
    scan_file(
        report,
        runtime_main / "ValidationEngine.java",
        [(r"@Cacheable\b", "R3 校验结果禁 Spring Cache（串结果 + 行为依赖宿主）")],
        label="ValidationEngine",
        repo=repo,
    )

    # R4: MetadataEngine 禁死配置三件套
    scan_file(
        report,
        runtime_main / "MetadataEngine.java",
        [
            (r"\bmaxRetries\b", "R4 死配置 maxRetries（全仓零消费者）"),
            (r"\bretryDelay\b", "R4 死配置 retryDelay（全仓零消费者）"),
            (r"\bexpressionEngineCache\b", "R4 死配置 expressionEngineCache（全仓零消费者）"),
        ],
        label="MetadataEngine",
        repo=repo,
    )

    if not report.ok:
        report.info(
            "修复指引：结果缓存一律摘除；确有性能诉求时用确定性键缓存"
            "（方法引用/解析产物），禁止以 hashCode() 做结果键。"
            "历史背景见 95e4779a（摘除）与本次哨兵动机注释。"
        )


def self_test(report: Report) -> None:
    """门禁自身存活验证（参照 check-gate-liveness 的变异验证思路）。

    用独立的 Report 做两组对照，自身保持干净（结束 ok=True ⇒ exit 0）：
      · 阳性对照（tainted）：注入一处 R4 死配置，断言被判红——证明判据有牙、非假绿；
      · 阴性对照（clean）：只含确定性键缓存等允许内容，断言被放行——证明未引入误报回归。
    """
    import tempfile

    d = Path(tempfile.mkdtemp())
    base = (
        "bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime"
    )

    # 阳性对照：注入 maxRetries（R4），应被判红
    tainted_repo = d / "tainted"
    tainted_rt = tainted_repo / base
    tainted_rt.mkdir(parents=True)
    (tainted_rt / "MetadataEngine.java").write_text(
        "// 注释行不应误触发\n"
        "class MetadataEngine { int maxRetries = 3; /* 行内注释 */ }\n",
        encoding="utf-8",
    )
    tainted = Report()
    judge(tainted, argparse.Namespace(), repo=tainted_repo, runtime_main=tainted_rt)
    if tainted.ok:
        report.fail("self-test 阳性对照失败：注入的 maxRetries 未被判红（判据已失效/假绿）")
        return

    # 阴性对照：只含允许的确定性键缓存，应被放行（无违规）
    clean_repo = d / "clean"
    clean_rt = clean_repo / base
    clean_rt.mkdir(parents=True)
    (clean_rt / "MetadataEngine.java").write_text(
        "// 方法引用缓存是确定性键，允许保留\n"
        "class MetadataEngine { private final Map<String, Object> propertyAccessorCache = new HashMap<>(); }\n",
        encoding="utf-8",
    )
    clean = Report()
    judge(clean, argparse.Namespace(), repo=clean_repo, runtime_main=clean_rt)
    if not clean.ok:
        report.fail(f"self-test 阴性对照失败：清洁树被误报 {clean._failures}")
        return

    report.info("self-test 通过：判据能命中注入的 R4 死配置，且对清洁树零误报")


def main(argv=None) -> int:
    """向后兼容入口：返回退出码（0/1）。check.sh 通过它消费退出码。

    argv 显式化：单测直接调用 main() 时传空列表，避免 unittest/pytest 的进程参数
    （discover -s scripts/tests -v）被 gatekit 的 argparse 当成门禁参数解析而
    SystemExit(2)。命令行运行时由 __main__ 显式传入 sys.argv[1:]，
    --self-test / --report-only / --quiet 等参数照常生效。
    """
    return run(
        judge,
        description="结果缓存哨兵：确定性优先于概率性命中（D-7）",
        argv=[] if argv is None else list(argv),
    )


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
