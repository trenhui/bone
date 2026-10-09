#!/usr/bin/env python3
"""跨服务异常落点一致性门禁。

## 判据（为何需要这道门禁，2026-10-07 实测事故）

同一份异常在不同服务返回**不同的 errorCode**，会让前端 i18n 与监控聚合同时裂开：

- 前端：`errors['COMMON_VALIDATION_FAILED']` 在部分服务取不到翻译 ⇒ 退化成显示 key 本身；
- 监控：同一业务失败被聚合成两个口径，告警阈值与趋势图都失真；
- 契约：错误码是**跨系统契约**（错误码登记 §2「可聚合」「可 i18n」），不同服务返回不同值
  等于契约本身没定义。

实测事故：`DomainException` 在 bone-web 是 `400 + COMMON_CONFLICT`，
而 masterdata / system / iam / generator 四个服务自己的分支是 `400 + COMMON_VALIDATION_FAILED`。
且 `COMMON_CONFLICT` 在台账里定义的是 **409「版本/状态冲突」**，与 400 属**语义错配**。

## 校验什么

对每个 `@ExceptionHandler(XxxException.class)` 分支，提取它映射的
`(HTTP 状态, errorCode)`，按异常类型聚合，断言**同一异常类型在所有服务里映射一致**。

## 判据的边界（务必读，否则会误报）

- **只校验「同一个异常类型」的跨服务一致性**，不校验「不同异常类型是否该用同一个码」
  （`IllegalArgumentException` 用 400、`IllegalStateException` 用 500 是合理的）；
- **不要求所有服务都有该分支**（某服务没有 `DomainException` 分支是合法的——
  它可能被`@RestControllerAdvice(basePackages=...)` 限定了作用域）；
- **`Exception.class` / `Throwable` 兜底分支不参与**（兜底的"一致性"由
  「是否脱敏 + 是否 500」两项各自保证，跨服务码不必相同）；
- `@ExceptionHandler` 支持 `{A.class, B.class}` 多类型声明 ⇒ 逐类型展开；
-★ **排除「码随异常走」的分支**（`problem(status, e.getErrorCode(), ...)`）：
  码由异常在运行期携带，各服务按业务自己注册（同一个 `BizException` 在 masterdata
  解析成 404、在 iam 解析成 400 是**正常**的）⇒ 这不是不一致，纳入校验纯误报。
  识别不出来就会把「动态派发」误判成「各写各的」，门禁必然被关掉——这本身就是假门禁；
- ★ **扫描前剥注释**：已删除的分支常在 javadoc 里留有说明文字（含注解字样），
  不剥注释会把「说明已删除」当成「存在分支」⇒ 报出根本不存在的假阳性。

## 逃生舱（登记即豁免，理由必填）

确有历史原因要保留不一致时，在此登记 `异常类型 -> 理由`；
登记后仍会在报告里列出（信息不隐藏），但不再阻断。
★豁免表只可收缩：代码已不存在却仍在豁免里 ⇒ 与 baseline 同为违规。
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

# ★ 兜底分支不参与一致性校验（理由见模块 docstring「判据的边界」）
FALLBACK_TYPES = {"Exception", "Throwable", "Error"}

# ★ 豁免表：异常全限定名 -> 豁免理由。移除条目即视为新增违规（只可收缩）。
EXEMPTIONS: dict[str, str] = {
    # 例：'com.foo.BizException': '历史原因：...（附 ADR 编号）'
}

# ★ 存量冻结：全仓有 7 处既有不一致（多为「同一异常在老模块用通用码、
# 新模块用专用码」的历史分层）。**一次全改风险过高**（每个都要业务判断，
# 且牵动前端 i18n 键与监控看板）⇒ 基线冻结、只阻断新增，逐条按业务优先级消债。
# 格式 {"异常类型": {"允许的映射集合": [[status, code], ...]}}
# ★ 只可收缩：代码已不存在却仍在基线里 ⇒ 与「豁免失效」同等违规。
BASELINE: dict[str, set[tuple[str, str]]] = {
    # 存量不一致 7 类（2026-07 快照，逐条消债；移除条目即视为该类已修复）
    "BizException": {("400", "VALIDATION_FAILED"), ("404", "NOT_FOUND")},
    "HttpMessageNotReadableException": {
        ("400", "MALFORMED_REQUEST"),
        ("400", "VALIDATION_FAILED"),
    },
    "HttpRequestMethodNotSupportedException": {
        ("400", "VALIDATION_FAILED"),
        ("405", "METHOD_NOT_ALLOWED"),
    },
    "MethodArgumentTypeMismatchException": {
        ("400", "MALFORMED_REQUEST"),
        ("400", "VALIDATION_FAILED"),
    },
    "NoResourceFoundException": {("400", "VALIDATION_FAILED"), ("404", "NOT_FOUND")},
    "SystemException": {("400", "VALIDATION_FAILED"), ("500", "INTERNAL_ERROR")},
}

# 异常类型 -> 允许出现的 (status, code) 组合集合；同类型出现多个不同组合即违规
ENUM_RE = re.compile(r"@ExceptionHandler\s*\(\s*(?:value\s*=\s*)?\{?(.*?)\}?\s*\)", re.S)
CLASS_RE = re.compile(r"([A-Za-z_][\w.]*)\.class")
SIG_RE = re.compile(r"public\s+[^\n(]*\(")
# 从方法体开头找 problemResponse(...) / problem(...) 的第一个实参对
MAP_RE = re.compile(
    r"(?:problemResponse|problem)\s*\(\s*(?:HttpStatus\.[A-Z_]+\s*,)?\s*"
    r"(\d{3})\s*,\s*([A-Za-z_][\w.]*(?:\.[A-Z_]+)?)",
    re.S,
)
ADVICE_RE = re.compile(r"@RestControllerAdvice(?:\(([^)]*)\))?")
# ★「码随异常走」：problem(status, e.getErrorCode(), ...) —— 码是运行期由异常携带的，
# 各服务天然不同（同一 BizException 在 masterdata 是 404、在 iam 是 400），
# **不是不一致** ⇒ 必须排除，否则门禁纯误报（实测首次运行 6 处里多数是这类假阳性）。
DYNAMIC_CODE_RE = re.compile(
    r"problem(?:Response)?\s*\(\s*[^,]+,\s*\w+\.(?:getErrorCode|getCode)\s*\)", re.S)

SKIP_DIRS = {"target", "node_modules", "dist", "build", ".git", "generated-code"}

# ★ 扫描前必须剥掉注释。实测踩过：iam 的 NotFoundException 分支**已被删除**，
# 但那段说明文字里仍留着 `@ExceptionHandler(NotFoundException.class)` 字样
# ⇒ 不剥注释就会把「注释里说明已删除」当成「有真实分支」，进而报出
# 「iam 缺 NotFoundException 分支」这种根本不存在的假阳性。
# 判据：注释里出现注解字样 ≠ 存在该注解。
_BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.S)
_LINE_COMMENT_RE = re.compile(r"//[^\n]*")


def strip_comments(src: str) -> str:
    """剥掉块注释与行注释，**保留行数**（用等量空行替换，便于行号仍可读）。"""
    src = _BLOCK_COMMENT_RE.sub(lambda m: "\n" * m.group(0).count("\n"), src)
    return _LINE_COMMENT_RE.sub("", src)
SCAN_ROOTS = ["bone-framework", "bone-platform", "bone-engine", "bone-blueprint"]


def module_of(path: Path) -> str:
    parts = path.parts
    for anchor in ("bone-framework", "bone-platform", "bone-engine", "bone-blueprint"):
        if anchor in parts:
            i = parts.index(anchor)
            rest = [p for p in parts[i + 1 :] if p not in ("src", "main", "java")]
            return f"{anchor}/" + rest[0] if rest else anchor
    return str(path.parent)


def scan_handlers() -> dict[str, list[tuple[str, int, str, str]]]:
    """异常全名 -> [(模块, 行号, status, code)]"""
    found: dict[str, list[tuple[str, int, str, str]]] = defaultdict(list)
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for path in base.rglob("*ExceptionHandler.java"):
            if SKIP_DIRS & set(path.parts):
                continue
            raw = path.read_text(encoding="utf-8", errors="ignore")
            if "@RestControllerAdvice" not in raw:
                continue
            src = strip_comments(raw)
            mod = module_of(path)
            lines = src.splitlines()
            for m in ENUM_RE.finditer(src):
                types = CLASS_RE.findall(m.group(1))
                if not types:
                    continue
                # 定位该注解所属方法：向下找最近的 public 签名，再取其方法体
                tail = src[m.end() :]
                sig = SIG_RE.search(tail)
                if not sig:
                    continue
                body = tail[sig.end() : sig.end() + 4000]
                # ★ 跳过「码随异常走」的分支：其码由异常携带，跨服务本就不同
                if DYNAMIC_CODE_RE.search(body[:2000]):
                    continue
                mapped = MAP_RE.search(body)
                if not mapped:
                    continue
                status, code = mapped.group(1), mapped.group(2)
                line_no = src[: m.start()].count("\n") + 1
                for t in types:
                    simple = t.rsplit(".", 1)[-1]
                    if simple in FALLBACK_TYPES:
                        continue
                    found[t].append((mod, line_no, status, code.rsplit(".", 1)[-1]))
    return found


def main() -> int:
    found = scan_handlers()
    violations: list[str] = []
    exempt_used: set[str] = set()

    frozen: list[str] = []
    for exc, entries in sorted(found.items()):
        mappings = {(e[2], e[3]) for e in entries}
        if len(mappings) <= 1:
            continue
        if exc in EXEMPTIONS:
            exempt_used.add(exc)
            continue
        # ★ 存量冻结：映射集合与基线完全一致 ⇒ 冻结（存量，继续报但不计新增）
        allowed = BASELINE.get(exc)
        if allowed is not None and mappings <= allowed:
            frozen.append(
                f"  {exc}\n      " + "；".join(
                    f"{m}:{ln} → {st}+{c}" for m, ln, st, c in sorted(set(entries))
                )
            )
            continue
        extra = mappings - (allowed or set())
        detail = "；".join(
            f"{m}:{ln} → {st}+{c}" for m, ln, st, c in sorted(set(entries))
        )
        violations.append(
            f"  {exc}\n      {detail}\n"
            f"      ★ 新增映射（不在基线内）: {sorted(extra)}"
        )

    # 豁免条目失效（代码已不存在）⇒ 与 baseline 同为违规，防止豁免表只增不减
    # 豁免条目失效（异常已无不一致 ⇒ 已修复，条目该移除）
    stale = [e for e in EXEMPTIONS if e not in found]
    # ★ 基线条目失效：对应异常已统一（不再有多种映射）⇒ 债务已还，应从基线移除，
    # 否则基线会变成永不缩小的黑名单（与「豁免只可收缩」同一条纪律）。
    stale_baseline = [
        e for e in BASELINE if e in found and len({(x[2], x[3]) for x in found[e]}) <= 1
    ]

    print("=" * 70)
    print("跨服务异常落点一致性门禁（同一异常类型 ⇒ 同一 status+errorCode）")
    print(f"  扫描异常分支: {sum(len(v) for v in found.values())} 个 / {len(found)} 种类型")
    print(f"  豁免登记: {len(EXEMPTIONS)} 条（本轮命中 {len(exempt_used)} 条）")
    print(f"  存量冻结: {len(BASELINE)} 类")
    print("=" * 70)

    ok = True
    if violations:
        ok = False
        print(f"\n❌ 发现 {len(violations)} 处跨服务映射不一致：")
        print("\n".join(violations))
        print(
            "\n修复方式：把该异常类型在各服务统一到同一 (status, errorCode)。\n"
            "         参考 RFC 9457 / 错误码登记 §2「同一错误语义跨服务必须同码」——\n"
            "         前端 i18n 与监控聚合都依赖这一点。\n"
            "         确需保留差异时，在本脚本 EXEMPTIONS 登记 异常类型 -> 理由（理由必填）。"
        )
    if frozen:
        print(f"\nℹ️ 存量冻结（基线内，{len(frozen)} 类不计违规）:")
        print("\n".join(frozen))
    if stale:
        ok = False
        print(f"\n❌ 豁免条目失效（代码已不存在却仍在豁免里）: {stale}")
        print("   请移除对应条目——豁免表只可收缩，否则会变成永不缩小的黑名单。")
    if stale_baseline:
        ok = False
        print(
            f"\n❌ 基线条目已失效（该异常已统一，债务已还却仍在基线里）: {stale_baseline}"
        )
        print("   请从 BASELINE 移除对应条目——基线只可收缩，否则会掩盖新的不一致。")

    if ok:
        print("\n✅ 同一异常类型在各服务映射一致")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())