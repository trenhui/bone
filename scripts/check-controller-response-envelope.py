#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：HC-003 —— Controller 端点必须返回统一响应信封，且信封载荷不得是领域对象。

背景
----
`gate-state.json` 自述该规则 **Planned（无实现）**：`controllerMustReturnApiResponse`
不在共享ArchUnit 规则库中。本次补齐实现，使其从"口头约定"变为"机器可阻断"。

规则
----
Controller 的**HTTP 端点方法**（带 `@GetMapping`/`@PostMapping`/`@PutMapping`/
`@DeleteMapping`/`@PatchMapping`）返回类型必须是统一信封：
`ApiResponse<T>` / `PageResult<T>`，或 `void`（无响应体）。

判为**合规**的非信封形态（逐条给理由，不是通配放行）：

============================  ==========================================
形态                          理由
============================  ==========================================
``ResponseEntity<ApiResponse<T>>``  仍是统一信封，只额外带 HTTP 状态码
``ResponseEntity<Void>``           无内容（204/异步触发），信封无处安放
``ResponseEntity<byte[]>/<Resource>``  文件下载流，响应体是二进制不是业务数据
============================  ==========================================

**方法级豁免**：`JwksController#jwks` —— RFC 7517 / OIDC 规定的 JWKS 端点，
响应体必须是标准 JWK Set，包一层 `ApiResponse` 会让所有消费方（网关、各微服务）
解析失败。登记在 baseline 的 `methodExemptions`，附 ADR-0005 依据。

信封到位还不够：**载荷不得是「内部端口契约类型」**（2026-10-16 第二判据，阻断级：
``..domain.gateway..`` 包，如 ``ActionDefSpec``——那是 domain 与 infrastructure 之间的协作
契约，不是 API 类型，直出 web 边界等于把内部协议固化成对外契约）。

``..domain..`` 下其余载荷（贫血持久化实体 / 数据对象，如各 ``*Repository`` 返回的 SDK
实体）**只做报告级盘点，不阻断**。理由（业界共识，2026-10-16 复核后收紧口径）：
- 这类实体无懒加载图、无敏感字段（字段级暴露由基类 ``@JsonIgnore`` 管控），序列化风险
  接近零；
- 前后端同仓发布的内部管理面 API，实体即线格式时强制 1:1 DTO 是纯仪式——每个字段要改
  两处，而线格式一行未变（曾据本门禁初版做过 8 个 1:1 View，已回退）；
- DTO 只在「契约确实需要与模型分化」时建：合并投影（如 ``ReferenceValueView`` 合并
  平台值 + 租户值）、聚合读模型、更名/裁剪、对外稳定消费者（ADR-0040 SDK）。
报告级清单用于让团队看见存量分布，逐个判断是否值得分化，而不是默认全部建 View。

三类必须排除的假阳性（否则 baseline 会被噪声淹没，真违规反而看不见）
----------------------------------------------------------------------
1. **嵌套 record/enum/interface**：`public record AckShipmentReq(...)` 形似方法签名，
   实为 DTO 定义。判据：返回类型为 record/enum/interface/class 且首字母大写。
2. **类内 public 辅助方法**：`public String render(...)` 没有映射注解 ⇒ 不是端点。
3. **名为 Controller 但无 Spring 注解的 POJO**：实测 `MetadataVersionController`
   **零 Spring 注解**，是纯算法类（版本号生成 / 兼容性检查），其 public 方法不构成 HTTP 端点。
   判据：类上必须有 ``@RestController`` 或 ``@Controller``。

存量baseline 只可收缩：新增端点不得加入；条目被修复后应从 baseline 移除。

用法::

    python3 scripts/check-controller-response-envelope.py# 阻断（新增违规）
    python3 scripts/check-controller-response-envelope.py --report-only# 恒 exit 0
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO / "doc/architecture/controller-response-baseline.json"

SCAN_ROOTS = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]

METHOD_RE = re.compile(r"public\s+(?!class\b)([\w<>,\[\]\.\s]+?)\s+(\w+)\s*\(", re.S)
MAPPING_ANNOTATIONS = (
    "@GetMapping",
    "@PostMapping",
    "@PutMapping",
    "@DeleteMapping",
    "@PatchMapping",
)

# 无响应体/二进制的 ResponseEntity 载荷 —— 合规，理由见模块 docstring
ENVELOPE_LESS_LOADS = {"Void", "void", "byte[]", "ByteArrayResource", "Resource"}


def normalize_return_type(raw: str) -> str:
    """去修饰噪声 + 去全限定名包前缀，得到可比对文本。"""
    t = re.sub(r"\s+", " ", raw).strip()
    return re.sub(r"\b([a-z][\w]*\.)+([A-Z]\w*)", r"\2", t)


def envelope_ok(return_type: str) -> bool:
    t = return_type.replace(" ", "")
    if t.startswith("ResponseEntity<"):
        inner = t[len("ResponseEntity<") : -1] if t.endswith(">") else t[len("ResponseEntity<") :]
        inner = normalize_return_type(inner)
        return (
            inner.startswith("ApiResponse")
            or inner.startswith("PageResult")
            or inner in ENVELOPE_LESS_LOADS
        )
    return t.startswith("ApiResponse") or t.startswith("PageResult") or t == "void"


def has_mapping_annotation(src: str, method_start: int) -> bool:
    """方法级映射注解（类级 @RequestMapping 只定前缀，不构成端点）。

    ⚠️ **不能用 ``rfind('}')`` 回溯**（2026-10-06 实测踩过）：javadoc 里的 ``{@link Xxx}``
    同样含 ``}``，会把注解区切断 ⇒ 有 @PostMapping 也判成False ⇒ **门禁假绿**。
    改为「从方法签名行往上逐行回溯，遇到空行/注释块/其它注解即停」，只看紧邻签名的注解区。
    """
    head = src[:method_start]
    lines = head.split("\n")
    # 从签名所在行的上一行往上，最多回溯 12 行（覆盖 @Operation/@PreAuthorize/@XxxMapping 组合）
    for line in reversed(lines[-13:-1]):
        stripped = line.strip()
        if not stripped:
            # 空行：注解区结束（签名正上方不应有空行）
            continue
        if any(a in stripped for a in MAPPING_ANNOTATIONS):
            return True
        # 注释块的结尾（*/）之前继续；一旦看到 javadoc/text 的起始则说明已越过注解区
        if stripped.startswith("*") or stripped.startswith("/*") or stripped.startswith("//"):
            continue
        if stripped.startswith("@"):
            continue  # 其它注解（如 @PreAuthorize / @Operation）
        if stripped.startswith("public ") or stripped.startswith("private "):
            return False  # 上一个成员声明，注解区已越过
    return False


def is_type_declaration(return_type: str, method_name: str) -> bool:
    """嵌套 record/enum/interface/class 定义（形似签名但不是端点）。"""
    return (
        method_name[:1].isupper()
        and return_type in ("record", "enum", "interface", "class")
    )


# ── 第二判据：载荷不得是内部端口契约类型 ──────────────────────────
# 两级口径（2026-10-16 复核收紧，理由见模块 docstring）：
#   gateway  —— ..domain.gateway.. 端口契约类型（如 ActionDefSpec）：阻断级，入 baseline；
#   domain   —— ..domain.. 其余载荷（贫血实体/数据对象）：报告级盘点，不阻断、不入 baseline。
# 判据只认「可证明的」：从文件 import 表解析简单名的全限定名。同包同名类（无 import）
# 无法证明，不判——宁漏不误报（假阳性会淹没 baseline，真违规反而看不见）。
IMPORT_RE = re.compile(r"^import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
GATEWAY_SEGMENT = ".domain.gateway."
DOMAIN_SEGMENT = ".domain."

# 逐层剥壳时视为「容器」的外层类型：信封 / 集合 / 分页。剥到标量或领域类型为止。
CONTAINER_TYPES = {
    "ApiResponse",
    "PageResult",
    "ResponseEntity",
    "List",
    "Set",
    "Collection",
    "Iterable",
    "Page",
}


def build_import_map(src: str) -> dict[str, str]:
    """简单类名 → 全限定名（同简单名多处 import 时后者覆盖，取任一致判据仍成立）。"""
    return {fqn.rsplit(".", 1)[-1]: fqn for fqn in IMPORT_RE.findall(src)}


def extract_payload(return_type: str) -> str | None:
    """从信封返回类型提取最终载荷简单名（逐层剥壳）。

    ``ApiResponse<Long>`` → ``Long``；``ApiResponse<List<ExtPoint>>`` → ``ExtPoint``；
    ``ApiResponse<PageResult<PluginExecutionLog>>`` → ``PluginExecutionLog``；
    ``ApiResponse<Map<String,Object>>`` → ``Map``（动态数据面，非领域对象）。
    """
    t = return_type.replace(" ", "").replace("\n", "")
    prev = None
    while prev != t:
        prev = t
        m = re.fullmatch(r"(\w+)<(.+)>", t)
        if not m:
            break
        outer, inner = m.group(1), m.group(2)
        if outer in CONTAINER_TYPES:
            t = inner
        else:
            break
    # 仍带泛型的（Map<K,V> 等）取外层类型名
    m = re.fullmatch(r"(\w+)<.*>", t)
    if m:
        t = m.group(1)
    return t or None


def payload_kind(payload: str | None, imports: dict[str, str]) -> str | None:
    """载荷简单名经 import 表解析后的分类：gateway / domain / None（非领域）。"""
    if not payload:
        return None
    fqn = imports.get(payload)
    if not fqn:
        return None
    if GATEWAY_SEGMENT in fqn:
        return "gateway"
    if DOMAIN_SEGMENT in fqn:
        return "domain"
    return None


def scan() -> list[dict]:
    import os

    rows: list[dict] = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
            dp = Path(dirpath)
            if "target" in dp.parts or "test" in dp.parts:
                continue
            for fn in filenames:
                if not fn.endswith("Controller.java"):
                    continue
                path = dp / fn
                src = path.read_text(encoding="utf-8")
                # 必须是真正的 Spring Controller（排除"名为 Controller 的 POJO"）
                if "@RestController" not in src and "@Controller" not in src:
                    continue
                rel = str(path.relative_to(REPO))
                imports = build_import_map(src)
                for m in METHOD_RE.finditer(src):
                    rt = normalize_return_type(m.group(1))
                    method = m.group(2)
                    if not rt or rt.startswith(("private", "protected")):
                        continue
                    if is_type_declaration(rt, method):
                        continue
                    if not has_mapping_annotation(src, m.start()):
                        continue
                    line_no = src[: m.start()].count("\n") + 1
                    row = {
                        "file": rel,
                        "method": method,
                        "returnType": rt,
                        "line": line_no,
                        "payload": None,
                        "payloadType": None,
                    }
                    # 分桶：信封违规（payload=None）/ 端口契约载荷（gateway，阻断）/
                    # domain 贫血实体载荷（domain，报告级）——三类互不覆盖。
                    if envelope_ok(rt):
                        payload = extract_payload(rt)
                        kind = payload_kind(payload, imports)
                        if kind is None:
                            continue
                        row["payload"] = kind
                        row["payloadType"] = payload
                    rows.append(row)
    rows.sort(key=lambda r: (r["file"], r["line"]))
    return rows


def load_baseline() -> tuple[set, set, dict]:
    if not BASELINE_PATH.exists():
        return set(), set(), {}
    data = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    entries = {
        f"{e['file']}::{e['method']}::{e['returnType']}" for e in data.get("endpoints", [])
    }
    payload_entries = {
        f"{e['file']}::{e['method']}::{e['returnType']}"
        for e in data.get("payloadViolations", [])
    }
    return entries, payload_entries, data


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument(
        "--update-baseline",
        action="store_true",
        help="按当前实测重写 baseline（仅供收敛存量时使用）",
    )
    ap.add_argument(
        "--update-payload-baseline",
        action="store_true",
        help="把当前实测的「信封载荷为领域对象」违规写入 baseline 的 payloadViolations（存量入账，只可收缩）",
    )
    args = ap.parse_args()

    rows = scan()
    envelope_rows = [r for r in rows if not r.get("payload")]
    gateway_rows = [r for r in rows if r.get("payload") == "gateway"]
    domain_rows = [r for r in rows if r.get("payload") == "domain"]

    if args.update_baseline:
        data = json.loads(BASELINE_PATH.read_text(encoding="utf-8")) if BASELINE_PATH.exists() else {}
        data["endpoints"] = [
            {"file": r["file"], "method": r["method"], "returnType": r["returnType"], "line": r["line"]}
            for r in envelope_rows
        ]
        BASELINE_PATH.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"baseline 已更新为当前实测：{len(envelope_rows)} 条")
        return 0

    if args.update_payload_baseline:
        data = json.loads(BASELINE_PATH.read_text(encoding="utf-8")) if BASELINE_PATH.exists() else {}
        data["payloadViolations"] = [
            {
                "file": r["file"],
                "method": r["method"],
                "returnType": r["returnType"],
                "payload": r["payloadType"],
                "line": r["line"],
                "reason": "存量：domain.gateway 端口契约类型直出 web 边界，补 *View/*Resp 后移除",
            }
            for r in gateway_rows
        ]
        BASELINE_PATH.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"payloadViolations 已更新为当前实测（仅 gateway 阻断级）：{len(gateway_rows)} 条")
        return 0

    baseline_entries, payload_baseline, data = load_baseline()
    exemptions = data.get("methodExemptions", {})

    new_violations = []
    baseline_only = []
    for r in envelope_rows:
        key = f"{r['file']}::{r['method']}::{r['returnType']}"
        if key in exemptions or f"{r['file']}::{r['method']}" in exemptions:
            continue
        if key in baseline_entries:
            baseline_only.append(r)
        else:
            new_violations.append(r)

    new_payload = []
    payload_baseline_only = []
    for r in gateway_rows:
        key = f"{r['file']}::{r['method']}::{r['returnType']}"
        if key in exemptions or f"{r['file']}::{r['method']}" in exemptions:
            continue
        if key in payload_baseline:
            payload_baseline_only.append(r)
        else:
            new_payload.append(r)

    # baseline里已不存在于代码的条目 = 已被修复，应从 baseline 移除（收缩）
    live_keys = {f"{r['file']}::{r['method']}::{r['returnType']}" for r in rows}
    stale = sorted(baseline_entries - live_keys)
    stale_payload = sorted(payload_baseline - live_keys)

    print("=" * 72)
    print("HC-003 Controller 统一响应门禁")
    print(f"  扫描目录: {', '.join(SCAN_ROOTS)}")
    print(f"  信封违规总数: {len(envelope_rows)}（存量基线 {len(baseline_only)} / 新增 {len(new_violations)}）")
    print(
        f"  端口契约载荷违规: {len(gateway_rows)}（存量基线 {len(payload_baseline_only)} / 新增 {len(new_payload)}）"
        " —— domain.gateway 类型直出，须补 *View/*Resp"
    )
    if domain_rows:
        by_file: dict[str, int] = {}
        for r in domain_rows:
            by_file[r["file"]] = by_file.get(r["file"], 0) + 1
        print(
            f"  [报告级] domain 贫血实体/数据对象载荷: {len(domain_rows)} 处"
            f"（{len(by_file)} 个控制器）——不阻断；契约需与模型分化时逐个建 DTO"
        )
        for f, n in sorted(by_file.items()):
            print(f"      {n:2d}  {f}")
    print(f"  方法级豁免: {len(exemptions)} 条")
    if stale:
        print(f"  ⚠️baseline 中已无对应代码（应移除，属基线收缩）: {len(stale)} 条")
        for s in stale[:10]:
            print(f"      {s}")
    if stale_payload:
        print(f"  ⚠️payloadViolations 中已无对应代码（应移除，属基线收缩）: {len(stale_payload)} 条")
        for s in stale_payload[:10]:
            print(f"      {s}")
    print("=" * 72)

    if new_violations or new_payload:
        total = len(new_violations) + len(new_payload)
        print(f"❌ 发现 {total} 个【新增】违规端点（不得加入 baseline）:")
        for r in new_violations + new_payload:
            print(f"   {r['file']}:{r['line']}")
            print(f"      {r['method']}() -> {r['returnType']}")
        print("\n修复方式：返回 ApiResponse<T> / PageResult<T>，且 T 不用 ..domain.gateway.. 类型；")
        print("         确属标准协议端点（响应体由协议规定）时，")
        print("         在 baseline 的 methodExemptions 登记并写明理由与 ADR 依据。")
        if not args.report_only:
            return 1

    if not new_violations and not new_payload:
        print("✅ 所有 Controller 端点均返回统一响应信封（存量豁免已登记）")

    return 0


if __name__ == "__main__":
    sys.exit(main())