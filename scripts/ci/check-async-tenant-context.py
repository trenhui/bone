#!/usr/bin/env python3
"""门禁：异步入口（AFTER_COMMIT + @Scheduled）必须显式声明租户。

## 为什么需要这条门禁（真实缺陷，非理论）

BONE 的 SDK 写路径按 ADR-0031 D3 **失败关闭**：缺租户直接抛
`IllegalArgumentException: tenantId must not be null: 异步入口必须显式声明租户`。
以下两类入口脱离 HTTP 请求线程，TenantContext 为 null：
- Spring `@TransactionalEventListener(phase = AFTER_COMMIT)`
- Spring `@Scheduled` 定时任务

若不显式 `TenantContextRunner.callAs(...)` 或显式 tenantId 参数，
**整条链路会静默失效**，只留一行 WARN 日志，表现为
「代码看起来没问题、编译通过、单测全绿，但功能从未生效」。

## 判据

对每个带 `@TransactionalEventListener(AFTER_COMMIT)` 或 `@Scheduled` 的类，
**若它直接调用 SDK 仓储/网关（依赖隐式 TenantContext 的读方法），
就必须出现 TenantContextRunner、显式 tenantId 参数、或委托给基础设施层已切租户的组件**。

## 合法写法（不报红，判据必须承认它们）

首轮跑出 5 个疑似违规（AFTER_COMMIT），逐个核实后确认其中 4 个是**合法**的，
本门禁第一版判据过宽，已修正。扩展到 @Scheduled 后新增第 4、5 类合法形态：

1. **TenantContextRunner.callAs/runAs**：直接在处理器/Job 方法体内显式切租户。

2. **委托给内部已切租户的组件**：如 `OrderCreatedEventHandler` 调
   `OrderItemInventoryExecutor.forEachItem(...)`，该执行器内部已切租户。
   ⇒ 类名出现在 DELEGATING_HELPERS 中即放行。

3. **方法签名显式收 tenantId**：如 `AuditLogRetentionPort.purgeExpired(Long tenantId, ...)`
   —— 这类出站端口把租户作为**参数**，不读隐式上下文，本就不需要 TenantContext。

4. **委托给基础设施层 Relay/RelayPort**：adapter 层 Schedule Job 委托给
   infrastructure 层的 `*Relay` / `*RelayPort`（如 `OrderOutboxRelayPortAdapter`），
   这些基础设施组件内部已统一处理 `.disableTenantFilter()` + `TenantContextRunner.callAs`。
   适配层不重复切租户，避免双层嵌套。（2026-10-08 新增）

5. **调用 *AllTenants 仓储入口**：如 `scheduleTaskRepository.findEnabledAllTenants()`、
   `flowMonitorSupport.getStatisticsForAllTenants()` —— 这些方法名以 AllTenants 结尾、
   内部已声明 `@TenantScope(ALL)` 或 `disableTenantFilter()`，语义就是跨租户扫描。（2026-10-08 新增）

6. **完全不碰租户数据**的纯通知/占位处理器：在 EXEMPT 中显式登记并写明理由。

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
# @Scheduled：Spring 定时任务入口，脱离 HTTP 请求线程，TenantContext 为 null，与 AFTER_COMMIT 同类约束。
SCHEDULED_RE = re.compile(r"@Scheduled\b")
# ★只认「真正调用」——不能匹配 import 行。
#   否则「留着一行没用的 import」就能骗过门禁（实测踩过：注入了违规代码门禁仍报绿，
#   因为 import 的 TenantContextRunner 被正则命中）。
TENANT_RUNNER_RE = re.compile(r"TenantContextRunner\s*\.\s*(?:callAs|runAs|run)\s*\(")
# 合法形态 4：adapter 层委托给 infrastructure 层的 Relay/RelayPort 出站端口。
# 这些基础设施组件内部已统一处理 disableTenantFilter() + TenantContextRunner.callAs。
DELEGATED_RELAY_RE = re.compile(r"\b\w*(?:Relay|RelayPort)\w*\s*\.\s*\w+\s*\(")
# 合法形态 5：调用 *AllTenants 仓储入口，方法名显式表明跨租户扫描。
ALL_TENANTS_RE = re.compile(r"\b\w*AllTenants\w*\s*\(")


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
            # 排除构建产物、依赖、测试、package-info（注解容器而非入口实现）。
            # package-info 只放 javadoc 和包级注解（如 @EnableScheduling），不含实际异步入口。
            s = str(p)
            if "/target/" in s or "/node_modules/" in s or "/src/test/" in s:
                continue
            if p.name == "package-info.java":
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


def _is_async_entry(text: str) -> bool:
    """判断是否为需要显式切租户的异步入口（AFTER_COMMIT 或 @Scheduled）。"""
    return bool(AFTER_COMMIT_RE.search(text) or SCHEDULED_RE.search(text))


def main() -> int:
    if not any(d.is_dir() for d in SRC_DIRS):
        print("❌ 未找到任何源码目录，门禁无法自检（路径失配会导致静默不检查）")
        return 1

    violations: list[tuple[str, str]] = []
    checked_after_commit = 0
    checked_scheduled = 0

    for f in iter_java_files():
        text = f.read_text(encoding="utf-8", errors="ignore")
        if not _is_async_entry(text):
            continue
        # 提取类名
        m = re.search(r"(?:public\s+)?(?:final\s+)?class\s+(\w+)", text)
        cls = m.group(1) if m else f.stem
        if AFTER_COMMIT_RE.search(text):
            checked_after_commit += 1
        if SCHEDULED_RE.search(text):
            checked_scheduled += 1
        if cls in EXEMPT:
            continue
        body = _code_without_imports(text)
        # 合法形态 1：直接用 TenantContextRunner
        if TENANT_RUNNER_RE.search(body):
            continue
        # 合法形态 2：委托给内部已切租户的辅助类
        if any(h in body for h in DELEGATING_HELPERS):
            continue
        # 合法形态 3：显式 tenantId 参数（方法签名带 tenantId 作参数）
        if _uses_explicit_tenant_id(body):
            continue
        # 合法形态 4：委托给 Relay/RelayPort 出站端口（基础设施层已切租户）
        if DELEGATED_RELAY_RE.search(body):
            continue
        # 合法形态 5：调用 *AllTenants 仓储入口（显式跨租户语义）
        if ALL_TENANTS_RE.search(body):
            continue
        violations.append((cls, str(f.relative_to(ROOT))))

    print("=" * 70)
    print("异步入口租户显式声明门禁（ADR-0031 D3）")
    print(f"  扫描目录: {', '.join(d.name for d in SRC_DIRS if d.is_dir())}")
    print(f"  AFTER_COMMIT 处理器: {checked_after_commit} 个")
    print(f"  @Scheduled 定时任务: {checked_scheduled} 个")
    print(f"  显式豁免: {len(EXEMPT)} 个")

    # 豁免键失配必须显式失败：否则 EXEMPT 里一个拼错的类名会静默失效，
    # 门禁看起来在跑、实际那条豁免根本没生效（与 check-gate-liveness 同类思路）。
    all_classes: dict[str, str] = {}
    for f in iter_java_files():
        text = f.read_text(encoding="utf-8", errors="ignore")
        if not _is_async_entry(text):
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
        print("\n❌ 以下异步入口未显式声明租户（会导致联动静默失效）：\n")
        for cls, path in violations:
            print(f"  · {cls}")
            print(f"    {path}")
        print("\n修法（任选其一）：")
        print("  ① TenantContextRunner.callAs(tenantId, () -> { ... }) —— 显式切租户")
        print("  ② 仓储方法签名首参带 tenantId（不读隐式上下文）")
        print("  ③ 委托给 infrastructure 层 Relay/RelayPort（内部已切租户）")
        print("  ④ 调用 *AllTenants 仓储入口（显式跨租户语义）")
        print("判据依据 doc/architecture/adr/ ADR-0031 D3。")
        return 1

    print(f"✅ 所有 {checked_after_commit} AFTER_COMMIT 处理器 + {checked_scheduled} @Scheduled 任务均已显式声明租户")
    print("=" * 70)
    return 0


if __name__ == "__main__":
    sys.exit(main())