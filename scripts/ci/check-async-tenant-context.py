#!/usr/bin/env python3
"""门禁：AFTER_COMMIT 事件处理器必须显式切租户。

## 为什么需要这条门禁（真实缺陷，非理论）

BONE 的 SDK 写路径按ADR-0031 D3 **失败关闭**：缺租户直接抛
`IllegalArgumentException: tenantId must not be null: 异步入口必须显式声明租户`。
而 Spring 的 `@TransactionalEventListener(AFTER_COMMIT)` 已脱离 HTTP 请求线程，
`TenantContext` 通常为 null ⇒ 处理器若不显式`TenantContextRunner.callAs(...)`，
**整条跨聚合联动会静默失效**，只留一行 WARN 日志，表现为
「代码看起来没问题、编译通过、单测全绿，但功能从未生效」。

2026-06-06 实测复现：`ShipmentOrderSyncEventHandler`（发货 → 订单状态联动）
最初漏切租户。把它改成 `callAs(null, ...)` 模拟"忘记切租户"后：
- E2E 精确报 2 条 FAIL（发货后订单未到 SHIPPED / 签收后未到 DELIVERED）
- 日志给出 `tenantId must not be null: 异步入口必须显式声明租户（ADR-0031 D3）`
⇒ 证明这条约束是**可证伪的硬约束**，不是风格偏好。

## 判据

对每个带 `@TransactionalEventListener(phase = AFTER_COMMIT)` 的类，
**若它直接调用 SDK 仓储/网关（依赖隐式 TenantContext 的读方法），
就必须出现 `TenantContextRunner`（callAs / runAs / run）**。

## 合法写法（不报红，判据必须承认它们）

首轮跑出 5 个疑似违规，逐个核实后确认其中 4 个是**合法**的，
本门禁第一版判据过宽，已修正。三类合法形态：

1. **委托给内部已切租户的组件**：如 `OrderCreatedEventHandler` /
   `OrderPaidEventHandler` 调`OrderItemInventoryExecutor.forEachItem(...)`，
   该执行器内部用 `TenantContextRunner.runAs`（见其类注释「AFTER_COMMIT 线程无 HTTP
   请求上下文…必须显式切换租户，否则 SDK Repository 会抛 MissingTenantContext」）。
   ⇒ 类名出现在 DELEGATING_HELPERS 中即放行。

2. **方法签名显式收tenantId**：如 `ChannelRoutedEventHandler` 调
   `channelRepository.findByCode(tenantId, event.channelCode())`——
   这类仓储方法把租户作为**参数**，不读隐式上下文，本就不需要 TenantContext。

3. **完全不碰租户数据**的纯通知处理器：在 EXEMPT 中显式登记并写明理由。

## 纪律

豁免必须**显式登记 + 写理由**，禁止「看起来像就不管」；
豁免键失配（EXEMPT 里写了不存在的类）必须显式失败。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC_DIRS = [
    ROOT / "bone-platform",
    ROOT / "bone-blueprint",
    ROOT / "bone-framework",
]

# 显式豁免：类名 -> 理由（必须写清"为何不碰租户数据"）。
# 以下处理器均不访问任何租户作用域仓储/网关，无需显式切租户；
# 若后续新增租户数据访问，须移出 EXEMPT 并补 TenantContextRunner.callAs/runAs。
EXEMPT: dict[str, str] = {
    # 纯事件发布器：仅委托 ApplicationEventPublisher.publishEvent，不碰租户持久化
    "SpringDomainEventPublisher": "纯事件发布实现，仅 applicationEventPublisher.publishEvent，不访问租户作用域仓储/网关",
    # masterdata 事件处理器：当前仅 log.info 事件载荷（占位 stub），无租户数据访问
    "MasterDataEntityCreatedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "DataQualityCheckCompletedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "MasterDataRecordCreatedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "MasterDataRecordPublishedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "DataQualityRuleCreatedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "MasterDataFieldAddedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
    "MasterDataEntityPublishedHandler": "当前仅 log.info 事件载荷，无租户作用域持久化访问",
}

# 内部已完成租户切换的辅助类：委托给它们的处理器不重复要求切租户
DELEGATING_HELPERS = ("OrderItemInventoryExecutor",)

# 默认 AFTER_COMMIT：Spring 的 @TransactionalEventListener 不写 phase 即 AFTER_COMMIT。
# 必须用「裸注解」也能命中，否则裸写法会逃过门禁（实测全仓 0 个写 phase=，门禁恒绿却查 0 个处理器 → 假绿）。
AFTER_COMMIT_RE = re.compile(r"@TransactionalEventListener\b")
# ★只认「真正调用」——不能匹配 import 行。
#   否则「留着一行没用的 import」就能骗过门禁（实测踩过：注入了违规代码门禁仍报绿，
#   因为 import 的 TenantContextRunner 被正则命中）。
TENANT_RUNNER_RE = re.compile(r"TenantContextRunner\s*\.\s*(?:callAs|runAs|run)\s*\(")


def _code_without_imports(text: str) -> str:
    """剥掉 import 行，避免"留个未使用的 import"骗过门禁。"""
    return "\n".join(
        line for line in text.split("\n") if not line.strip().startswith("import ")
    )


def iter_java_files() -> list[Path]:
    for base in SRC_DIRS:
        if not base.is_dir():
            continue
        for p in base.rglob("*.java"):
            # 排除构建产物、依赖与测试（测试不是生产处理器，不应受此门禁约束）
            s = str(p)
            if "/target/" in s or "/node_modules/" in s or "/src/test/" in s:
                continue
            yield p


def _uses_explicit_tenant_id(text: str) -> bool:
    """判断是否**显式携带租户**，而非依赖隐式 TenantContext。

    三种合法形态（均不读隐式上下文，故不需要 TenantContext）：
      a) 传给仓储/网关方法的首个实参：
         `channelRepository.findByCode(tenantId, event.code())`
      b) 传给领域对象工厂（写入实体自带 tenantId 字段）：
         `ConfigHistory.of(..., event.tenantId(), ...)`
         —— ConfigHistoryProjector 称此为「R2 显式租户」，脱离主链路/Outbox 重放也不丢租户。
      c) 局部变量显式改名后传参：`long tenantId = event.tenantId(); ...(tenantId, ...)`
    """
    # a) 仓储/网关调用首参为租户
    if re.search(
        r"\b\w*(?:Repository|Gateway|Port)\s*\.\s*\w+\s*\(\s*(?:tenantId|event\.tenantId\(\))\s*,",
        text,
    ):
        return True
    # b) 领域对象工厂里携带租户（写在实参位的 tenantId()/tenantId）
    if re.search(r"\b\w+\.of\([^;]{0,400}?tenantId", text, re.DOTALL):
        return True
    # c) 先赋值给局部 tenantId 再传参
    if re.search(r"\b(?:long|Long)\s+tenantId\s*=", text) and re.search(r"\(\s*tenantId\s*,", text):
        return True
    return False


def main() -> int:
    if not any(d.is_dir() for d in SRC_DIRS):
        print("❌ 未找到任何源码目录，门禁无法自检（路径失配会导致静默不检查）")
        return 1

    violations: list[tuple[str, str]] = []
    checked = 0

    for f in iter_java_files():
        text = f.read_text(encoding="utf-8", errors="ignore")
        if not AFTER_COMMIT_RE.search(text):
            continue
        # 提取类名
        m = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        cls = m.group(1) if m else f.stem
        checked += 1
        if cls in EXEMPT:
            continue
        body = _code_without_imports(text)
        if TENANT_RUNNER_RE.search(body):
            continue
        # 合法形态 1：委托给内部已切租户的辅助类
        if any(h in body for h in DELEGATING_HELPERS):
            continue
        # 合法形态 2：方法签名显式收 tenantId（租户作参数，不读隐式上下文）。
        # 判据：AFTER_COMMIT 监听方法体内出现 tenantId 局部变量，且被传给仓储/网关调用。
        # 这比"类里出现过 tenantId 字样"严——它要求 tenantId 真的参与调用。
        if _uses_explicit_tenant_id(body):
            continue
        violations.append((cls, str(f.relative_to(ROOT))))

    print("=" * 70)
    print("AFTER_COMMIT 处理器租户显式声明门禁")
    print(f"  扫描目录: {', '.join(d.name for d in SRC_DIRS if d.is_dir())}")
    print(f"  含 AFTER_COMMIT 的处理器类: {checked} 个")
    print(f"  显式豁免: {len(EXEMPT)} 个")

    # 豁免键失配必须显式失败：否则 EXEMPT 里一个拼错的类名会静默失效，
    # 门禁看起来在跑、实际那条豁免根本没生效（与 check-gate-liveness 同类思路）。
    all_classes: dict[str, str] = {}
    for f in iter_java_files():
        text = f.read_text(encoding="utf-8", errors="ignore")
        if not AFTER_COMMIT_RE.search(text):
            continue
        m = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        if m:
            all_classes[m.group(1)] = str(f.relative_to(ROOT))
    stale = [c for c in EXEMPT if c not in all_classes]
    if stale:
        print(f"\n❌ 豁免清单中的类已不存在（豁免键失配）：{', '.join(stale)}")
        print("   请更新 EXEMPT —— 留着会让该豁免静默失效。")
        return 1

    if violations:
        print("\n❌ 以下 AFTER_COMMIT 处理器未显式切租户（会导致联动静默失效）：\n")
        for cls, path in violations:
            print(f"  · {cls}")
            print(f"    {path}")
        print("\n修法：把处理器内的读写包进租户上下文，例如")
        print("    TenantContextRunner.callAs(event.tenantId(), () -> { ... })")
        print("判据依据见 doc/architecture/adr/ ADR-0031 D3（异步入口必须显式声明租户）。")
        return 1

    print("✅ 所有 AFTER_COMMIT 处理器均已显式声明租户（ADR-0031 D3）")
    print("=" * 70)
    return 0


if __name__ == "__main__":
    sys.exit(main())