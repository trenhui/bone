#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Spring Security ``/error`` 白名单门禁：防止"业务异常被误报成 401"。

缺陷背景（2026-10-06 在 bone-file 实测确诊）
---------------------------------------------
Spring Boot 在 Controller 抛异常后会把请求 **FORWARD** 到 ``/error``，由
``BasicErrorController`` / ``DefaultHandlerExceptionResolver`` 渲染成真正的错误响应。
若 ``/error`` 不在 Spring Security 的 ``permitAll`` 白名单里，这条 FORWARD 会被
``AuthorizationFilter`` 拦下⇒ ``AuthorizationDeniedException`` ⇒ **对外表现为 401**。

因果链（每一环都可在日志中观测）::

    认证通过 (AnonymousAuthenticationFilter: already authenticated)
      → Controller 抛业务异常（对象不存在等）
      → FORWARD /error
      → /error 不在 permitAll ⇒ AuthorizationDeniedException
      → 401「未认证」

**危害不是"多了一个 401"，而是把真实的 404 / 403 / 500 统统掩盖成"未认证"**：
排查时会被系统性误导到"密钥不一致 / 权限码缺失 / token 有问题"等错误方向。
本次即因此绕了 5 轮，最终靠 ``logging.level.org.springframework.security=TRACE``
才定位到真因（``already authenticated`` 紧跟 ``Securing GET /error``）。

为什么此前只有 bone-file 暴露
------------------------------
其余模块都有 ``@RestControllerAdvice``（bone-web 的 ``globalExceptionHandler``）
在**过滤器链内**就把异常转成 ``ApiResponse``，不会走到 FORWARD ``/error``；
而 file 的 ``download`` 端点直接写 ``HttpServletResponse``
（``in.transferTo(out)``），异常路径上没有兜底 advice ⇒ 必然 FORWARD ``/error``。
**「没暴露」不等于「没缺陷」** —— 任何一个新增的直写响应体的端点都会立刻暴露它，
所以本门禁对全仓所有 ``SecurityConfig`` 一律要求。

判据
----
凡声明 ``.authorizeHttpRequests(...)`` 的 ``SecurityConfig``，其 ``permitAll`` 白名单
必须包含 ``/error``。

豁免
----
1. **整体放行型配置**：若该filter chain 的收口是 ``anyRequest().permitAll()``
   （联调/匿名 profile），``/error`` 天然可通，无需显式列出 —— 判据不误报这类。
2. **非Web 上下文**：不含 ``authorizeHttpRequests`` 的类（如仅提供 bean 的配置类）不在检查范围。

负向探针（写完后必须自证，见文件末尾说明）
----------------------------------------
删掉任一模块的 ``"/error"`` 那一行 ⇒ 本门禁必须**只**报该模块一个，
其余全绿（证明判据不过度敏感、也没有漏检）。
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

# 参与检查的模块（每个含 SecurityConfig 的可独立启动服务）。
# 新增模块时同步追加 —— 漏了不会报错，只会"该模块不被检查"，故在末尾自检里提示。
SECURITY_CONFIG_GLOBS = [
    "bone-platform/bone-iam",
    "bone-platform/bone-system",
    "bone-platform/bone-masterdata",
    "bone-platform/bone-integration",
    "bone-platform/bone-file",
    "bone-platform/bone-gateway",
    "bone-engine/bone-metadata-server",
    "bone-engine/studio-generator",
    "bone-engine/bone-extension-engine/bone-extension-studio",
    "bone-blueprint",
]

# 判定"整体放行"的收口形态：出现它则 /error 天然可通。
ANY_REQUEST_PERMIT_ALL = re.compile(r"anyRequest\(\)\s*\.permitAll\(\)")

# authorizeHttpRequests 的起点；用它切出授权配置块，避免被类内其它方法干扰。
AUTHORIZE_START = re.compile(r"\.authorizeHttpRequests\(")


def collect_security_configs():
    """返回 [(模块名, 文件路径, 源码)]，去重且排除测试与target。"""
    seen = {}
    for module in SECURITY_CONFIG_GLOBS:
        module_dir = REPO_ROOT / module
        if not module_dir.is_dir():
            continue
        for path in module_dir.rglob("SecurityConfig.java"):
            text = str(path)
            if "/test/" in text or "/target/" in text:
                continue
            seen[path] = path.read_text(encoding="utf-8")
    return seen


def find_authorize_blocks(source):
    """切出每个 ``.authorizeHttpRequests(...)`` 块的源码文本（括号配平）。

    不用正则硬匹配整个调用（嵌套括号 + lambda 体让正则极易截断），
    改为从 ``.authorizeHttpRequests(`` 起始处做括号配平扫描。
    """
    blocks = []
    for match in AUTHORIZE_START.finditer(source):
        start = match.end() - 1  # 指向 '('
        depth = 0
        for idx in range(start, len(source)):
            ch = source[idx]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    blocks.append(source[start : idx + 1])
                    break
        else:
            # 括号未配平：源码被截断或格式异常。返回已取到的部分，
            # 判据随后会因"找不到 /error"而报红——这是期望行为（宁可误报不可漏检）。
            blocks.append(source[start:])
    return blocks


def check_one(module, path, source):
    """返回该文件的问题列表（空 = 通过）。"""
    blocks = find_authorize_blocks(source)
    if not blocks:
        # 不含 authorizeHttpRequests：非 Web 安全配置（如仅注册 bean），不在检查范围。
        return []

    problems = []
    for block in blocks:
        if ANY_REQUEST_PERMIT_ALL.search(block):
            # 整体放行型（联调/匿名 profile）：/error 天然可通，豁免。
            continue
        if re.search(r'"/error"', block):
            continue
        problems.append(block)
    return problems


def main():
    configs = collect_security_configs()
    if not configs:
        print("❌ 未找到任何 SecurityConfig.java —— 判据失效（模块路径是否变了？）")
        return 1

    failures = []
    checked = 0
    for path, source in sorted(configs.items()):
        rel = path.relative_to(REPO_ROOT)
        module = rel.parts[0] if rel.parts[0] != "bone-platform" and rel.parts[0] != "bone-engine" else rel.parts[1]
        problems = check_one(module, path, source)
        blocks = find_authorize_blocks(source)
        if not blocks:
            continue
        # 只有存在「需要显式放行」的块才计入分母。
        needs_check = [b for b in blocks if not ANY_REQUEST_PERMIT_ALL.search(b)]
        if not needs_check:
            continue
        checked += 1
        if problems:
            failures.append((module, rel, len(problems)))

    # 自检：配置的模块清单不能有失效路径（拼错模块名会让该模块静默不被检查）。
    missing = [m for m in SECURITY_CONFIG_GLOBS if not (REPO_ROOT / m).is_dir()]
    if missing:
        print(f"❌ SECURITY_CONFIG_GLOBS 里这些模块路径不存在：{missing}")
        print("   （路径写错会让对应模块**静默不被检查**，属门禁假绿）")
        return 1

    if failures:
        print("❌ 以下 SecurityConfig 的 permitAll 白名单缺少 \"/error\"：")
        print()
        print("   危害：Controller 抛业务异常 → FORWARD /error → 被 Security 拦下")
        print("        → 返回 401「未认证」，把真实的 404/403/500 全部掩盖。")
        print("        2026-10-06 在 bone-file 上实测确诊，排查时曾误导为「密钥不一致」。")
        print()
        for module, rel, count in failures:
            print(f"   · {module:24s} {rel}（{count} 个授权块缺 /error）")
        print()
        print("   修法：在该SecurityConfig 的 permitAll 白名单里加 \"/error\"")
        print("   （样板见 bone-platform/bone-file/.../config/SecurityConfig.java）")
        return 1

    print(f"✅ SecurityConfig /error 白名单：{checked} 个授权配置全部已放行")
    return 0


if __name__ == "__main__":
    sys.exit(main())
