#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""URL 层permitAll 不得覆盖含写端点的业务路径（R5）。

背景
----
Spring Security 有**两层**独立生效的授权：

  ① URL 层`authorizeHttpRequests().requestMatchers(...).permitAll()`
     —— 只作用于 `AuthorizationFilter`，决定"要不要认证"。
  ② 方法层 `@PreAuthorize` —— 走 `MethodInterceptor`，决定"这次调用准不准"。

一个常见误解是「只要写了 `@PreAuthorize` 就安全」，因为 ② 确实仍生效。
真正的风险在于**两者叠加时的维护脆弱性**：一旦某个业务路径前缀被 ① 放行，
该前缀下的安全边界就从「一条规则」退化为「每个方法各写一次注解」——
将来任何人新增端点漏写 `@PreAuthorize`，别的路径漏注解只是
「任何登录用户可写」，**被 permitAll 覆盖的路径漏注解则是「任何人可写」**，
爆炸半径差一个量级。

本仓真实案例（P0-4，2026-10-04 修复）
------------------------------------
`bone-iam` 的 `SecurityConfig` 曾把 `/api/v1/apps/**` 放进 `permitAll`，
而该前缀下有 8 个写端点（App 的 create/update/delete + grant/revokePermission
+ Module 的增删改）。当时全部挂了 `@PreAuthorize`，所以未被利用；
但它是一颗**已经上膛的枪**——`SystemController.java:126-128` 自己就记录过
「即使当前恒 501 也先挂门禁」这类真实漏法，说明该类漏洞在本仓**已经发生过**。
本次已移除该放行；本门禁防止它被重新加回来。

判据
----
R5：对每个 `*SecurityConfig.java`（main/java），提取 `permitAll()` 之前
最近一次 `requestMatchers(...)` 的**全部路径字面量**，若其中存在「覆盖写端点的
业务路径」⇒ FAIL。

「覆盖写端点的业务路径」判定（两条同时满足，避免误报）：
  a) 该路径以 `/**` 或 `/*` 结尾，**或**其中含路径段（`/`）——
     精确单路径（如 `/actuator/health`）不构成"前缀放行"，只豁免精确匹配；
  b) 它**不是**下述公认需要匿名的基础设施路径（见 INFRA_PREFIXES）。

为什么不用「禁止一切 permitAll」
--------------------------
本仓有大量**正当**的匿名面：健康检查、swagger/openapi、h2-console、
CORS预检OPTIONS、登录端点、支付回调（由验签而非登录保护）。
一刀切禁permitAll 会把这些一起打红，迫使人写豁免清单 —— 而豁免清单本身
又会退化成「什么都往里塞」。故本门禁只约束 **b 意义上的"前缀放行"**，
把精确路径留给人工判断。

豁免
----
`src/test/**` 全部豁免（测试替身）。此外 `*.properties` / `*.yml` 不在扫描范围
（yml 侧的放行由 check-security-fail-open-default.py 负责）。

用法
----
    python3 scripts/check-controller-authorization-url-bypass.py            # 报告
    python3 scripts/check-controller-authorization-url-bypass.py --strict    # 有违规即 exit 1
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

JAVA_ROOTS = (
    "bone-platform",
    "bone-engine",
    "bone-framework",
    "bone-blueprint",
)

# 公认应当匿名的基础设施路径前缀：这些不承载业务写操作，
# 匿名是设计意图（探针、API 文档、控制台、预检）。
INFRA_PREFIXES = (
    "/actuator",
    "/swagger-ui",
    "/v3/api-docs",
    "/v2/api-docs",
    "/h2-console",
    "/actuator/health",
    "/.well-known",
)

# 明确允许匿名且**不承载写端点**的精确路径（登录/回调/公开元数据等）。
# 逐条写明理由——豁免本身需要理由，否则清单会退化成「什么都往里塞」。
ALLOWED_EXACT = {
    "/api/v1/iam/login": "登录端点：匿名是设计意图（凭证校验在方法内完成）",
    "/api/v1/iam/sso/callback": "SSO 回调：由 state/nonce 校验保护，非登录态可访问",
    "/api/v1/iam/sso/config": "SSO 配置公开元数据（供客户端构造跳转），不含敏感信息",
    "/api/v1/payments/callback": "支付回调：由渠道验签（HMAC）保护，且已有来源 IP 白名单",
    "/api/v1/metadata/health": "元数据服务健康检查，供探针调用",
    "/api/v1/integration/**": "集成域：由集成层自有鉴权与共享密钥保护（见 integration SecurityConfig 注释）",
    "/api/v1/extension/**": "扩展上报数据面：由 ReporterTokenFilter 共享密钥守成失败关闭",
    "/api/v1/marketplace/**": "扩展市场：只读公开数据 + 上报通道（共享密钥保护）",
    "/api/v1/deployment-status/**": "部署状态上报：只读 + 共享密钥保护的机器身份通道",
    "/v1/integration/**": "集成域出站回调：验签保护",
    "/v1/auth/**": "元数据服务自身鉴权端点",
}

COMMENT_LINE = re.compile(r"//[^\n]*")
COMMENT_BLOCK = re.compile(r"/\*.*?\*/", re.S)

# requestMatchers(...) / antMatchers(...) 的调用（可跨多行）
MATCHERS_CALL = re.compile(r"\.(?:requestMatchers|antMatchers)\s*\(", re.S)
PERMIT_ALL = re.compile(r"\.permitAll\s*\(\s*\)")
STRING_LITERAL = re.compile(r'"([^"]*)"')

# 判定「前缀放行」：以 /*或 ** 结尾，或路径中含段分隔
def _is_prefix_pattern(path: str) -> bool:
    if not path.startswith("/"):
        return False
    if path.endswith("/**") or path.endswith("/*"):
        return True
    # 含段分隔且不是精确单层路径 ⇒ 视为前缀/深层放行
    return path.count("/") >= 2


def _iter_security_configs():
    for root in JAVA_ROOTS:
        base = REPO / root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*SecurityConfig.java")):
            posix = path.relative_to(REPO).as_posix()
            if "/src/main/java/" not in posix:
                continue
            yield path


def _extract_permitted_paths(code: str) -> list[tuple[str, int]]:
    """返回 [(路径, 行号)]：所有经 permitAll() 放行的路径字面量。

    做法：在代码中定位每个 `.permitAll()`，向前找最近一次
    `.requestMatchers(...)` / `.antMatchers(...)` 的调用实参，取其中的字符串字面量。
    """
    results: list[tuple[str, int]] = []
    for pa in PERMIT_ALL.finditer(code):
        # 从 permitAll 位置向前回溯到最近的 matchers 调用
        prefix = code[: pa.start()]
        calls = list(MATCHERS_CALL.finditer(prefix))
        if not calls:
            continue
        call = calls[-1]
        # 该matchers 实参的括号范围
        i = call.end() - 1  # 指向 '('
        depth = 0
        j = i
        while j < len(code):
            if code[j] == "(":
                depth += 1
            elif code[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        args = code[i + 1 : j]
        # 只取本文件顶层（防止把嵌套方法体的 requestMatchers 也算进来）——
        # 精确做法：限制 args 中不含 lambda 体（=> / {）
        if "->" in args or "{" in args:
            args = args.split("->")[0].split("{")[0]
        for m in STRING_LITERAL.finditer(args):
            path = m.group(1)
            lineno = code[: i + 1 + m.start()].count("\n") + 1
            results.append((path, lineno))
    return results


def check_file(path: Path) -> list[str]:
    violations: list[str] = []
    rel = path.relative_to(REPO)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:  # pragma: no cover
        return [f"{rel}: 读取失败 {exc}"]

    code = COMMENT_LINE.sub("", COMMENT_BLOCK.sub("", text))
    for p, lineno in _extract_permitted_paths(code):
        if not _is_prefix_pattern(p):
            continue  # 精确单路径：匿名是设计意图，人工判断即可
        if any(p.startswith(x) for x in INFRA_PREFIXES):
            continue
        if p in ALLOWED_EXACT:
            continue
        violations.append(
            f"{rel}:{lineno} permitAll() 覆盖了前缀形式的业务路径 `{p}`（R5）。"
            f"该前缀下若有写端点（@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping），"
            f"则「漏写@PreAuthorize」的爆炸半径从『任何登录用户可写』升级为"
            f"『任何人可写』——与方法层注解是否已写无关，因为 URL 层已放行认证。"
            f"处置：① 若该路径确实需要匿名（如机器身份数据面），把它登记进 "
            f"ALLOWED_EXACT 并写明由什么保护（共享密钥/验签）；"
            f"② 否则从 permitAll 移除，让它落在 anyRequest().authenticated() 之下。"
        )
    return violations


def main() -> int:
    parser = argparse.ArgumentParser(
        description="URL 层 permitAll 不得覆盖含写端点的业务路径前缀"
    )
    parser.add_argument("--strict", action="store_true", help="有违规即 exit 1（门禁模式）")
    args = parser.parse_args()

    all_violations: list[str] = []
    scanned = 0
    paths_seen = 0
    for path in _iter_security_configs():
        scanned += 1
        try:
            code = path.read_text(encoding="utf-8")
            code = COMMENT_LINE.sub("", COMMENT_BLOCK.sub("", code))
            paths_seen += len(_extract_permitted_paths(code))
        except OSError:
            pass
        all_violations.extend(check_file(path))

    print(
        f"扫描 {scanned} 个 SecurityConfig，{paths_seen} 个 permitAll 路径字面量"
    )
    if all_violations:
        print(f"\n❌ 发现 {len(all_violations)} 处违规：\n")
        for v in all_violations:
            print(f"  · {v}")
        if args.strict:
            return 1
        return 0

    print("✅ URL 层 permitAll 未覆盖含写端点的业务路径前缀")
    return 0


if __name__ == "__main__":
    sys.exit(main())
