#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""控制器细粒度授权门禁：写端点必须声明 @PreAuthorize。

背景（2026-10-03 实测）
----------------------
`SecurityConfig` 各模块统一是 ``anyRequest().authenticated()`` —— **认证强制、
授权不强制**。这意味着「任何登录用户」都能调用没有 ``@PreAuthorize`` 的端点。
本门禁把这条约定变成机器可校验的规则：任何 ``@Post/@Put/@Patch/@Delete``
端点若其**方法级或所属类级**没有 ``@PreAuthorize``，即失败。

为什么只管写端点
----------------
读端点存在大量**已裁定的合理例外**（`DictItemController` 的字典项是每个表单都要读的
基础数据，权限目录只登记了 `sys:dict:write`，挂一个未登记的读码会让所有消费方 403）。
把读端点纳入门禁会产出大量无法在不破坏功能的前提下修复的噪声，反而导致门禁被
整体关掉。写端点无此争议 —— 任何写操作都必须回答「谁有权写」。

判据要点（三次踩坑后定稿，见模块常量区注释）
------------------------------------------------
1. **类级 ``@PreAuthorize`` 对所有方法生效** —— 必须识别，否则误报
   （`ConsoleController` 类级 ``sys:console:read`` 覆盖 5 个端点）。
2. **注解顺序有两种形态** —— ``@PreAuthorize`` 在 ``@XxxMapping`` 之前（实测 45 处）
   与之后（实测 88 处）都合法。只在Mapping 之后找会漏掉前者。
3. **必须按方法块结算，不能按固定字符窗口** —— 窗口法会把下一个方法的注解
   算到上一个方法上（实测让13/13 全覆盖的 AlertController 误报 4 处）。
   正解：以「方法签名行」为结算点，并跳过方法体。

基线
----
存量缺口记入 ``controller-authorization-baseline.json``（键为
``相对路径#HTTP方法:映射路径``），门禁只拦**新增**与**基线外的存量**。
基线只可收缩：修复即从基线移除条目，不允许改为 ``resolved``。

用法::

    python3 scripts/check-controller-authorization.py            # 校验
    python3 scripts/check-controller-authorization.py --report-only
    python3 scripts/check-controller-authorization.py --baseline   # 重生成基线
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO_ROOT / "doc" / "architecture" / "controller-authorization-baseline.json"

# 扫描根：所有含 Controller 的业务模块。**不能只列一部分** ——
# 漏掉某个模块等于给它开后门（实测漏了 `bone-file`，而它有完整 JWT 栈、
# 2 个写端点确实缺授权）。
# 唯一排除 `bone-framework/bone-web`：它只有全局异常处理器的 advice，
# 没有任何业务端点。
SCAN_ROOTS = (
    "bone-platform",
    "bone-engine",
    "bone-blueprint",
)

# 明确豁免：这些 Controller 的写端点按设计不对外暴露 / 属公开端点，
# 逐条给出理由。**豁免必须写理由** —— 无理由的豁免等于把门禁关掉。
#
# ⚠️ 类级豁免的代价：EXEMPT 以**文件名**匹配，命中即跳过整个文件。
# 只有当该文件的**全部**写端点都合理豁免时才能用（逐个核过，不是"看起来"）。
EXEMPT: dict[str, str] = {
    "JwksController.java": "JWKS 是 OIDC/OAuth2 公开元数据端点，任何方都需无认证读取（RFC 8414）",
    "AuthController.java": "认证端点本身（login/refresh/logout）—— 授权发生在这一步，"
    "再要求 @PreAuthorize 会形成循环依赖",
    # 原HealthController 已删除，健康检查并入 SystemController#health（GET，不在写端点范围），
    # 其豁免条目曾长期滞留 —— 类级豁免失配判据就是被它逼出来的。
    "E2eEntity1202624423478541Controller.java": "e2e 专用 fixture 端点，不在生产路由注册表内",
    "ExtensionE2eController.java": "e2e 专用端点，同上",
    # 站内信：授权对象就是 JWT 主体本身，归属校验在服务层强制，不存在可登记的权限码。
    # 该控制器只有 1 个写端点（POST /{id}/read），且 userId 不可由调用方指定
    # （2026-10-03 修 IDOR：原先是 @RequestParam，归属校验拿入参比对，形同虚设）。
    # 挂一个 "notification:message:read" 之类的码反而会放宽安全模型 ——
    # 任何持有该码的用户就能读**别人**的站内信，而正确语义是"只能读自己的"。
    "NotificationController.java": "站内信写端点的授权依据是 JWT 主体归属（服务层强制），"
    "非权限码；挂码反而允许跨用户读取",
}

# 方法级豁免：键为 ``文件名#HTTP方法:映射路径``（与基线 entries 同构，便于机器核对），
# 值为豁免理由。**只在类级豁免会连带放过已加固端点时使用** ——
# 例如 ExtensionManagementController 有 13 个写端点已挂scope，整类豁免等于把门禁关掉。
#
# 键随路径解析变化而失效（届时会以「新增/未登记」红灯暴露），这是刻意的：
# 豁免失效必须以门禁失败的形式暴露，不能静默生效。
EXEMPT_METHODS: dict[str, str] = {
    # 数据面上报通道：业务进程（bone-extension-sdk StudioExecutionLogReporter）以**进程身份**
    # 异步上报，不持有终端用户 JWT，SecurityConfig 对这两个路径显式 permitAll。
    # 控制面（人操作扩展）与数据面（机器上报）分通道是业界常规做法；
    # 安全边界由内网/网关白名单保障，不是终端用户权限能表达的语义。
    "ExtensionManagementController.java#POST:": "数据面执行日志上报：进程身份 + SecurityConfig 显式"
    "permitAll（非终端用户 JWT 可表达的授权），边界在内网/网关层",
    # MFA 注册/验证：社区版未上线，两个端点恒抛 IAM_MFA_NOT_AVAILABLE（HTTP 501），
    # 不读主体、不落库、不产生任何状态变更 —— 无可授权的资源，故不挂权限码。
    # 前端不暴露入口（见该类 javadoc）。
    "MfaController.java#POST:/enroll": "能力未上线，恒返回 501，无状态变更，不存在可授权资源",
    "MfaController.java#POST:/verify": "能力未上线，恒返回 501，无状态变更，不存在可授权资源",
    # /me 自助端点：主体就是JWT 主体本身，accountId 取自 CurrentPrincipalPort，
    # **不可由入参指定**，故不存在越权面；正确语义是"任何登录用户都能改自己的资料"，
    # 挂权限码会把"改自己的密码"变成需要额外授权，反而降低安全性。
    "MeController.java#PUT:": "自助端点：主体取自 JWT（CurrentPrincipalPort），accountId 不可由入参指定；"
    "挂码会把「改自己的资料」变成需额外授权",
    "MeController.java#POST:/change-password": "同上：主体取自 JWT 的账号自助改密（旧密码校验在服务层）",
}

WRITE_VERBS = {"Post", "Put", "Patch", "Delete"}

MAP_RE = re.compile(r"@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:\"([^\"]*)\"|(\w+))?\s*\))?")
PRE_AUTH_RE = re.compile(r'@PreAuthorize\(\s*"([^"]+)"\s*\)')
# 在**剥离了字符串字面量与注释**的文本上匹配注解是否存在 —— 不能带引号参数要求
#（剥离后只剩`@PreAuthorize()`）。
# 必须排除 import 行：`import org.springframework.security.access.prepost.PreAuthorize;`
# 含同样的 `@PreAuthorize` 字样（实测导致 DictItemController 被误判为「类级已授权」）。
# 判据：行首为可选空白后紧跟 `@PreAuthorize(`。
PRE_AUTH_LOOSE = re.compile(r"^\s*@PreAuthorize\s*\(", re.MULTILINE)


def iter_controllers():
    for root in SCAN_ROOTS:
        base = REPO_ROOT / root
        if not base.is_dir():
            continue
        for p in sorted(base.rglob("*Controller.java")):
            if "/target/" in p.as_posix() or "/test/" in p.as_posix():
                continue
            yield p


def _skip_string_and_comment(line: str, in_block_comment: bool) -> tuple[bool, bool]:
    """粗略跳过字符串字面量与行/块注释，避免注释里的注解/字符串干扰切分。"""
    out = []
    i = 0
    n = len(line)
    in_str = False
    while i < n:
        c = line[i]
        if in_block_comment:
            if line.startswith("*/", i):
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if line.startswith("//", i):
            break
        if line.startswith("/*", i):
            in_block_comment = True
            i += 2
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out), in_block_comment


def _skip_method_body(lines: list[str], start: int) -> int:
    """从 ``start`` 行（方法体起始行）起，跳过整个方法体，返回其后第一行下标。"""
    depth = 0
    in_block_comment = False
    for i in range(start, len(lines)):
        code, in_block_comment = _skip_string_and_comment(lines[i], in_block_comment)
        depth += code.count("{") - code.count("}")
        if depth <= 0 and i >= start:
            return i + 1
    return len(lines)


def scan_file(path: pathlib.Path) -> list[tuple[str, str, str]]:
    """返回该文件的 ``(method, path, name)`` 缺口三元组列表。

    判据分离两个文本域 —— 混用会导致「剥掉字符串后正则匹配不到」或
    「URL 提取为空」两类静默失效（两者都已实测踩过）：
      · **结构域**（剥离字符串/注释后的 ``code``）：判定注解与花括号位置。
        ``@PreAuthorize("...")`` 在此变成 ``@PreAuthorize()``，故用宽松正则。
      * **文本域**（原始 ``raw``）：提取映射路径。
    """
    lines = path.read_text(encoding="utf-8").split("\n")

    # 1) 类级 @PreAuthorize（出现在 class 声明之前）=> 对所有端点生效。
    #    必须基于**剥离注释后的**文本：javadoc 里的 {@code @PreAuthorize} 字样
    #    不是真实注解（实测DictItemController 因此被误判为「类级已授权」）。
    cls_idx = next((i for i, l in enumerate(lines) if re.search(r"\bclass\s+\w+", l)), len(lines))
    _head_code: list[str] = []
    _bc = False
    for l in lines[:cls_idx]:
        c, _bc = _skip_string_and_comment(l, _bc)
        _head_code.append(c)
    class_auth = PRE_AUTH_LOOSE.search("\n".join(_head_code))

    violations: list[tuple[str, str, str]] = []
    name = path.name

    # 2) 逐方法扫描：累积注解（结构域 + 文本域），到方法签名行结算
    pending_code: list[str] = []
    pending_raw: list[str] = []
    i = 0
    in_block_comment = False
    bc = False
    while i < len(lines):
        raw = lines[i]
        code, in_block_comment = _skip_string_and_comment(raw, in_block_comment)
        st = code.strip()

        if st.startswith("@"):
            pending_code.append(code)
            pending_raw.append(raw)
            i += 1
            continue

        if st == "" or st.startswith("*") or st.startswith("/*") or st.startswith("//"):
            # 注释/空行不清空pending —— 注解块内部常夹注释
            i += 1
            continue

        if "(" in code and "class" not in st and "interface" not in st:
            # 方法签名 —— 可能跨多行（`public X upload(` 后接参数行才出现 `{`），
            # 故累积签名行直到见到 `{` 再结算。实测 FileController#upload
            # 签名跨 3 行，按单行判据会漏掉并导致后续扫描整体错位。
            sig = st
            j = i
            while "{" not in sig and j + 1 < len(lines):
                j += 1
                nxt, bc = _skip_string_and_comment(lines[j], bc)
                sig += " " + nxt.strip()
            blk_code = "\n".join(pending_code)
            blk_raw = "\n".join(pending_raw)
            m_map = None
            for cand in MAP_RE.finditer(blk_raw):
                m_map = cand
            if (
                m_map is not None
                and not class_auth
                and not PRE_AUTH_LOOSE.search(blk_code)
            ):
                verb = m_map.group(1)
                if verb in WRITE_VERBS:
                    url = (
                        m_map.group(2)
                        if m_map.group(2) is not None
                        else (m_map.group(3) or "")
                    )
                    violations.append((verb.upper(), url, name))
            pending_code, pending_raw = [], []
            i = _skip_method_body(lines, j)
            continue

        # 字段声明 / import / 其他：清空 pending
        pending_code, pending_raw = [], []
        i += 1

    return violations


def key_for(path: pathlib.Path, verb: str, url: str) -> str:
    rel = path.relative_to(REPO_ROOT).as_posix()
    return f"{rel}#{verb}:{url}"


def _short_key(key: str) -> str:
    """全路径键 → 短键（``文件名#METHOD:路径``）。已是短键的原样返回。

    路径前缀（如 ``bone-platform/bone-iam/``）与授权判定无关，却会让基线在一次目录
    调整后整体失配、引发数十条假红灯。短键让基线只随**授权语义**变化而失败。
    """
    if "#" not in key:
        return key
    head, tail = key.split("#", 1)
    return f"{head.split('/')[-1]}#{tail}"


def main() -> int:
    ap = argparse.ArgumentParser(description="控制器写端点必须声明 @PreAuthorize")
    ap.add_argument("--report-only", action="store_true", help="只报告不失败")
    ap.add_argument("--baseline", action="store_true", help="重生成基线文件")
    args = ap.parse_args()

    all_gaps: dict[str, str] = {}
    for p in iter_controllers():
        for verb, url, _name in scan_file(p):
            # 短键（文件名#METHOD:path）：豁免键与基线 entries 都以此为准 ——
            # 豁免必须能跨仓库搬迁（文件名不变、路径变），用全路径会让一次目录调整静默废掉全部豁免。
            all_gaps[f"{p.name}#{verb.upper()}:{url}"] = p.name

    found = {
        k: v
        for k, v in all_gaps.items()
        if k.split("#", 1)[0] not in EXEMPT and k not in EXEMPT_METHODS
    }
    # 基线里已存在的老式全路径键：兼容读取，输出时统一为短键，
    # 这样「路径前缀变化」不会引发假红灯（基线本来就只应当被显式编辑，不是自动重生成）。
    baseline_raw: dict[str, str] = {}
    if BASELINE_PATH.is_file():
        baseline_raw = json.loads(BASELINE_PATH.read_text(encoding="utf-8")).get("entries", {})
    baseline = {_short_key(k): v for k, v in baseline_raw.items()}

    # 方法级豁免键若失配（文件名写错 / 端点已加注解 / 路径解析规则变化），
    # 必须显式失败 —— 否则「豁免」会退化成「门禁看不见这个端点」，比缺授权更难发现。
    stale_exempt = sorted(k for k in EXEMPT_METHODS if k not in all_gaps)

    # 类级豁免同理：控制器被删/改名后条目会静默留下（实测 HealthController 早已并入
    # SystemController#health，豁免条目却一直挂着）。此时若同名文件日后被新建，
    # 新文件会**直接继承旧豁免**而不被检查 —— 必须在条目失配时就报错。
    stale_exempt_cls = sorted(n for n in EXEMPT if not any(p.name == n for p in iter_controllers()))

    if args.baseline:
        payload = {
            "_说明": (
                "控制器写端点缺 @PreAuthorize 的存量基线。门禁只拦新增与基线外存量；"
                "修复后应从本文件移除对应条目（只可收缩，不允许改为 resolved）。"
                "键为短键 `文件名#METHOD:路径`（不含仓库相对目录，便于跨目录搬迁）。"
                "豁免分两类，唯一真源在 check-controller-authorization.py 的 EXEMPT（类级）/ "
                "EXEMPT_METHODS（方法级），逐条带理由。"
            ),
            "exempt": EXEMPT,
            "exempt_methods": EXEMPT_METHODS,
            "entries": dict(sorted(found.items())),
        }
        BASELINE_PATH.parent.mkdir(parents=True, exist_ok=True)
        BASELINE_PATH.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"✅ 已重生成基线：{len(found)} 条 -> {BASELINE_PATH.relative_to(REPO_ROOT)}")
        return 0

    new_entries = sorted(set(found) - set(baseline))
    resolved = sorted(set(baseline) - set(found))

    if stale_exempt or stale_exempt_cls or new_entries or resolved:
        if stale_exempt:
            print("❌ 方法级豁免键失效（端点不存在、已加 @PreAuthorize，或路径解析变化）：")
            for k in stale_exempt:
                print(f"   - {k}")
        if stale_exempt_cls:
            print("❌ 类级豁免条目失效（控制器已删除或改名；同名文件若日后重建会白继承豁免）：")
            for k in stale_exempt_cls:
                print(f"   - {k}")
        if new_entries or resolved:
            print("❌ 控制器授权门禁失败：")
            for k in new_entries:
                print(f"   - [新增/未登记] {k}")
            for k in resolved:
                print(f"   - [已修复，请从基线移除] {k}")
            print(f"   存量未登记 {len(new_entries)} 条 / 可收缩 {len(resolved)} 条")
        return 0 if args.report_only else 1

    print(
        f"✅ 控制器授权门禁通过：写端点均声明 @PreAuthorize"
        f"（存量基线 {len(baseline)} 条，类级豁免 {len(EXEMPT)} 个 Controller，"
        f"方法级豁免 {len(EXEMPT_METHODS)} 条）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
