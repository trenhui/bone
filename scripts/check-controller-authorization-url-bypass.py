#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""URL 层 permitAll 不得覆盖含写端点的业务路径前缀（R5）。

背景
----
Spring Security 有**两层**独立生效的授权：

  ① URL 层 `authorizeHttpRequests().requestMatchers(...).permitAll()`
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
但它是一颗**已经上膛的枪**—— `SystemController.java:126-128` 自己就记录过
「即使当前恒 501 也先挂门禁」这类决策，而 `check-controller-authorization.py`
只能看方法层注解、看不见URL 层放行。本次已移除该放行；本门禁防止它被加回来。

判据设计：解析整条链，不用位置窗口
----------------------------------
**第一版曾把判据写成「从每个 permitAll() 向前回溯最近的 requestMatchers()」，
结果 14 处全是误报。** 根因有三，均为该设计的固有缺陷：

  ① `requestMatchers("/api/**").authenticated().anyRequest().permitAll()`
     —— 回溯只看 permitAll 附近的 matchers，**看不见中间的 `.authenticated()`**，
     于是把一个「先要求认证再兜底放行」的链误判成「/api/** 被匿名放行」。
  ② `requestMatchers(HttpMethod.OPTIONS, "/**")` —— CORS 预检，
     把 `"/**"` 当成了业务路径。
  ③ `count("/") >= 2` 就算「前缀」—— `/api/v1/extension/execution-logs:ingest`
     是精确路径却被判成前缀放行。

正确做法是**把 fluent 链按方法调用线性扫描**，维护一个 `pending` 路径集：
  - `requestMatchers(...)` / `antMatchers(...)` → 路径进 pending
  - `permitAll()` → **reporting** pending（清空）
  - 任何其他终结符（`authenticated()` / `fullyAuthenticated()` / `hasAuthority(...)`
    / `hasRole(...)` / `denyAll()`）→ **丢弃** pending（它们不匿名放行）

只有「最近一个终结符确实是 permitAll」时 pending 才被上报，误报从根上消除。

「前缀形式」判定
--------------
只有以 `/**` 或 `/*` 结尾才算前缀放行。`/api/v1/apps/{id}/permissions` 这类
含段分隔但**精确匹配**的路径不是前缀放行（Spring 的 `{id}` 只是路径变量），
匿名与否由方法层决定，人工判断即可。

为什么不用「禁止一切 permitAll」
--------------------------
本仓有大量**正当**匿名面：CORS 预检、健康检查、swagger/openapi、h2-console、
登录端点、支付回调（验签保护）、机器身份数据面（共享密钥保护）。
一刀切会把这些一起打红，迫使人写豁免清单 —— 而豁免清单本身又会退化成
「什么都往里塞」。故本门禁只约束「前缀形式 + 业务路径 + 非已知保护面」。

豁免
----
- `src/test/**`（测试替身）
- `HttpMethod.OPTIONS` 开头的 matcher：CORS 预检不承载业务写端点。
- INFRA_PREFIXES：健康检查 / 文档 / 控制台等公认匿名基础设施面。
- ALLOWED_EXACT：逐条写明「由什么保护」的精确或前缀路径。

用法
----
    python3 scripts/check-controller-authorization-url-bypass.py# 报告
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

# 公认应当匿名的基础设施路径前缀（不是业务面，不承载写端点）。
INFRA_PREFIXES = (
    "/actuator",
    "/swagger-ui",
    "/api-docs",
    "/v3/api-docs",
    "/v2/api-docs",
    "/h2-console",
    "/.well-known",
    "/error",
)

# 明确允许匿名的业务路径 —— 逐条写明「由什么保护」，
# 否则这份清单会退化成「什么都往里塞」。
ALLOWED_EXACT = {
    "/api/v1/iam/login": "登录端点：凭证校验在方法内完成，匿名是设计意图",
    "/api/v1/iam/sso/callback": "SSO 回调：由 state/nonce 校验保护",
    "/api/v1/iam/sso/config": "SSO 公开配置元数据（供客户端构造跳转），不含敏感信息",
    "/api/v1/payments/callback": "支付回调：由渠道 HMAC 验签保护 + 来源 IP 白名单",
    "/api/v1/metadata/health": "元数据服务健康检查，供容器探针调用",
    "/api/v1/extension/**": "扩展数据面：permitAll 但由 ReporterTokenFilter 共享密钥守成失败关闭"
    "（未配置共享密钥时返 503，令牌错返 401，见 extension-studio SecurityConfig 注释）",
    "/api/v1/extension/execution-logs:ingest": "机器身份执行日志上报：同上报通道，由 ReporterTokenFilter 共享密钥保护",
    "/api/v1/extension/execution-logs/ingest": "机器身份执行日志上报（同上，两种路径写法）",
    "/api/v1/marketplace/**": "扩展市场：只读公开数据；上报侧由共享密钥保护",
    "/api/v1/deployment-status/**": "部署状态上报：机器身份通道，共享密钥保护",
    "/v1/integration/**": "集成域出站回调：由验签保护（integration SecurityConfig 注释）",
    "/v1/auth/**": "元数据服务自身鉴权端点",
}

COMMENT_LINE = re.compile(r"//[^\n]*")
COMMENT_BLOCK = re.compile(r"/\*.*?\*/", re.S)

# 链上的终结符。permitAll 之外的终结符都会「丢弃 pending」——
# 因为它们不构成匿名放行（authenticated 要求认证，hasAuthority 要求权限）。
TERMINATORS = (
    "permitAll",
    "authenticated",
    "fullyAuthenticated",
    "denyAll",
    "hasAuthority",
    "hasRole",
    "hasAnyAuthority",
    "hasAnyRole",
    "access",
)

CALL_RE = re.compile(
    r"\.(?:requestMatchers|antMatchers|permitAll|authenticated|fullyAuthenticated"
    r"|denyAll|hasAuthority|hasRole|hasAnyAuthority|hasAnyRole|access)\b"
    r"[A-Za-z0-9_]*\s*\("
)
STRING_LITERAL = re.compile(r'"([^"]*)"')


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


def _match_args(code: str, open_paren_idx: int) -> tuple[str, int]:
    """从 '(' 处取到配对 ')' 的实参文本。返回 (args, close_idx)。"""
    depth = 0
    i = open_paren_idx
    while i < len(code):
        if code[i] == "(":
            depth += 1
        elif code[i] == ")":
            depth -= 1
            if depth == 0:
                return code[open_paren_idx + 1 : i], i
        i += 1
    return code[open_paren_idx + 1 :], len(code)


def _is_prefix_pattern(path: str) -> bool:
    """仅 `/**` / `/*` 结尾算前缀放行。

    `{id}` 之类的路径变量仍是精确匹配（Spring 会在运行时替换成实际值），
    因此 `/api/v1/apps/{id}/permissions` 这类**不是**前缀放行。
    """
    return path.endswith("/**") or path.endswith("/*")


def _scan_chain(code: str) -> list[tuple[str, int]]:
    """线性扫描 authorizeHttpRequests 的 fluent 链，返回被 permitAll 放行的前缀路径。

    见模块 docstring「判据设计」：pending 集在遇到**任何**终结符时清空，
    只有最近的终结符是 permitAll 才上报 —— 这是与「位置窗口法」的本质区别。
    """
    start = code.find("authorizeHttpRequests")
    if start < 0:
        return []
    # 链的结束：securityFilterChain 方法体的闭合，或下一个 @Bean 方法
    scope = code[start:]
    # 截到本方法末尾（避免跨方法误扫）
    end = scope.find("\n  @Bean")
    if end > 0:
        scope = scope[:end]

    pending: list[tuple[str, int]] = []
    reported: list[tuple[str, int]] = []

    for m in CALL_RE.finditer(scope):
        name = re.match(r"\.\w+", m.group(0)).group(0)[1:]
        args, _ = _match_args(scope, m.end() - 1)

        if name in ("requestMatchers", "antMatchers"):
            # CORS 预检不承载业务写端点，两种写法都要排除：
            #   ① requestMatchers(HttpMethod.OPTIONS, "/**")
            #   ② new AntPathRequestMatcher("/**", "OPTIONS")  —— integration 模块用的是这种构造式
            if re.search(r"\bHttpMethod\s*\.\s*OPTIONS\b", args) or re.search(
                r'new\s+AntPathRequestMatcher\s*\([^)]*"\s*OPTIONS\s*"', args
            ):
                pending = []
                continue
            # 丢弃嵌套 lambda 内的内容，只保留本层的字符串字面量
            flat = args.split("->")[0].split("{")[0]
            for lit in STRING_LITERAL.finditer(flat):
                path = lit.group(1)
                if not path.startswith("/"):
                    continue
                lineno = code[: start + m.end() - 1 + lit.start()].count("\n") + 1
                pending.append((path, lineno))
        elif name == "permitAll":
            reported.extend(pending)
            pending = []
        else:
            # authenticated() / hasAuthority(...) 等：不匿名放行，丢弃 pending
            pending = []

    return reported


def check_file(path: Path) -> list[str]:
    violations: list[str] = []
    rel = path.relative_to(REPO)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:  # pragma: no cover
        return [f"{rel}: 读取失败 {exc}"]

    code = COMMENT_LINE.sub("", COMMENT_BLOCK.sub("", text))
    for p, lineno in _scan_chain(code):
        if not _is_prefix_pattern(p):
            continue
        if any(p.startswith(x) for x in INFRA_PREFIXES):
            continue
        if p in ALLOWED_EXACT:
            continue
        violations.append(
            f"{rel}:{lineno} permitAll() 覆盖了业务路径前缀 `{p}`（R5）。"
            f"该前缀下若存在写端点（@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping），"
            f"则「漏写 @PreAuthorize」的爆炸半径会从『任何登录用户可写』升级为"
            f"『任何人可写』——与方法层注解当前是否写了无关，"
            f"因为 URL 层已把认证放行，后续新增端点只需漏一个注解即失守。"
            f"处置：① 若该路径确实需要匿名（机器身份数据面等），登记进 ALLOWED_EXACT "
            f"并写明由什么保护（共享密钥/验签）；② 否则从 permitAll 移除，"
            f"让它落在 anyRequest().authenticated() 之下。"
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
    reported_paths = 0
    for path in _iter_security_configs():
        scanned += 1
        try:
            code = path.read_text(encoding="utf-8")
            code = COMMENT_LINE.sub("", COMMENT_BLOCK.sub("", code))
            reported_paths += len(_scan_chain(code))
        except OSError:
            pass
        all_violations.extend(check_file(path))

    print(f"扫描 {scanned} 个 SecurityConfig，{reported_paths} 个路径被 permitAll 放行")
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
