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

退出码：0=无违规；1=有违规（每个违规一行 file:line + 判据名）。
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
RUNTIME_MAIN = (
    REPO
    / "bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime"
)

violations = []


def scan_file(path: Path, rules, label: str) -> None:
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
                violations.append(
                    f"{path.relative_to(REPO)}:{lineno}: [{label}] {name}\n"
                    f"      {stripped}"
                )


def main() -> int:
    if not RUNTIME_MAIN.exists():
        print(f"❌ 锚定目录不存在（结构变更请同步更新本脚本锚点）: {RUNTIME_MAIN}")
        return 1

    # R1a: ExpressionEngine 禁结果缓存与 Spring Cache 通道
    expression_engine = RUNTIME_MAIN / "ExpressionEngine.java"
    scan_file(
        expression_engine,
        [
            (r"expressionResultCache", "R1a 结果缓存回归（hashCode 键可串结果）"),
            (r"EXPRESSION_CACHE_NAME", "R1a 结果缓存命名常量回归"),
            (r"\bCacheManager\b", "R1b Spring Cache 通道回归（CacheManager）"),
            (r"org\.springframework\.cache\.", "R1b Spring Cache 导入回归"),
            (r"context\.hashCode\(\)", "R1c hashCode 参与缓存键（概率性命中）"),
        ],
        label="ExpressionEngine",
    )

    # R1c: runtime 整包禁 Spring Cache 通道（覆盖未来新增类的旁路）
    for java in RUNTIME_MAIN.rglob("*.java"):
        scan_file(
            java,
            [
                (r"import\s+org\.springframework\.cache\.", "R1c Spring Cache 导入（runtime 整包禁用）"),
            ],
            label="runtime-package",
        )

    # R2: RuleEngine 三处求值必须走 eval / evaluateBooleanExpression
    scan_file(
        RUNTIME_MAIN / "RuleEngine.java",
        [
            (
                r"\.evaluateExpression\s*\(",
                "R2 参数求值必须走 eval/evaluateBooleanExpression（String 布尔比较恒 false）",
            ),
        ],
        label="RuleEngine",
    )

    # R3: ValidationEngine 禁 @Cacheable（hashCode 键 + @EnableCaching 由宿主决定）
    scan_file(
        RUNTIME_MAIN / "ValidationEngine.java",
        [(r"@Cacheable\b", "R3 校验结果禁 Spring Cache（串结果 + 行为依赖宿主）")],
        label="ValidationEngine",
    )

    # R4: MetadataEngine 禁死配置三件套
    scan_file(
        RUNTIME_MAIN / "MetadataEngine.java",
        [
            (r"\bmaxRetries\b", "R4 死配置 maxRetries（全仓零消费者）"),
            (r"\bretryDelay\b", "R4 死配置 retryDelay（全仓零消费者）"),
            (r"\bexpressionEngineCache\b", "R4 死配置 expressionEngineCache（全仓零消费者）"),
        ],
        label="MetadataEngine",
    )

    if violations:
        print(f"❌ 结果缓存哨兵：{len(violations)} 处违规（确定性优先于概率性命中）\n")
        for v in violations:
            print(f"  - {v}")
        print(
            "\n  修复指引：结果缓存一律摘除；确有性能诉求时用确定性键缓存"
            "（方法引用/解析产物），禁止以 hashCode() 做结果键。"
            "历史背景见 95e4779a（摘除）与本次哨兵动机注释。"
        )
        return 1

    print("✅ 结果缓存哨兵通过：无概率性结果缓存 / Spring Cache 通道 / 死配置回归")
    return 0


if __name__ == "__main__":
    sys.exit(main())
