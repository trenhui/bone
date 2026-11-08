#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""业务错误码「三处必齐」机检：后端常量 → HTTP 状态 → 前端双语包。

背景（2026-11-08 核实）：
  `ClaimErrorCodes.java` 里 `CLAIM_*` 是 **Java 常量名**，`SC_CLAIM_*` 是它的
  **字符串值**（稳定业务码 = i18n 键）。此前一度被误读成「前后端两套错误码
  体系」，实测是同一个东西的标识符与值，不是命名冲突。

  后端已有运行时守护：`ClaimErrors.checkEveryCodeRegistered()` 用反射逐个
  校验常量都在 `DEFAULT_HTTP_STATUS` 登记，漏一个类加载即抛。
  **但前端两个语言包没有 CI 门禁**——新增码漏 zh-CN 或 en-US，编译期和
  运行期都发现不了，只有用户触发该错误时才看到裸露的 `SC_*` 字符串。
  本脚本把最后一环补成可判定判据。

判据：
  E1 `ClaimErrorCodes` 声明的常量名集合 == `ClaimErrors` HTTP 登记集合（双向）
  E2/E3 每个 `SC_CLAIM_*` 值都在 zh-CN / en-US 的 `errors` 键里
  E4 zh-CN 与 en-US 的 `errors` 键集合完全一致（防只加一侧；覆盖全平台 276 键）
  E5 任一输入解析为空即 fail-closed，不按空集合恒过

不依赖数据库，可进 §六 全套门禁。
"""
import io
import json
import os
import re
import sys

import _manifest as M

REPO = os.path.abspath(os.path.join(M.ROOT, "..", "..", ".."))
CODES_JAVA = os.path.join(
    REPO, "bone-apps", "synap-claim", "synap-claim-app", "src", "main", "java",
    "com", "bone", "claim", "common", "ClaimErrorCodes.java")
ERRORS_JAVA = os.path.join(
    REPO, "bone-apps", "synap-claim", "synap-claim-app", "src", "main", "java",
    "com", "bone", "claim", "common", "ClaimErrors.java")
LOCALES = os.path.join(
    REPO, "bone-frontend", "packages", "shared-utils", "src", "i18n", "locales")

DECL_RE = re.compile(r'public static final String\s+(\w+)\s*=\s*"(SC_[A-Z0-9_]+)"\s*;')
ENTRY_RE = re.compile(r'Map\.entry\(\s*ClaimErrorCodes\.(\w+)\s*,')


def load():
    codes = dict(DECL_RE.findall(io.open(CODES_JAVA, encoding="utf-8").read()))
    registered = set(ENTRY_RE.findall(io.open(ERRORS_JAVA, encoding="utf-8").read()))
    locales = {}
    for lang in ("zh-CN", "en-US"):
        data = json.load(io.open(os.path.join(LOCALES, lang + ".json"), encoding="utf-8"))
        locales[lang] = set(data.get("errors", {}))
    return codes, registered, locales


def verdict(codes, registered, locales):
    """纯函数判定，便于 --self-test 反证。"""
    errs = []
    if not codes:
        return ["E5 ClaimErrorCodes 声明解析为空（路径/格式已变），fail-closed"]
    if not registered:
        return ["E5 ClaimErrors 的 DEFAULT_HTTP_STATUS 登记解析为空，fail-closed"]
    for lang, keys in locales.items():
        if not keys:
            return ["E5 %s 的 errors 段解析为空，fail-closed" % lang]

    declared = set(codes)
    if declared - registered:
        errs.append("E1 声明了但未登记 HTTP 状态（类加载会抛）：%s"
                    % sorted(declared - registered))
    if registered - declared:
        errs.append("E1 登记了不存在的常量：%s" % sorted(registered - declared))

    values = set(codes.values())
    claim_values = {v for v in values if v.startswith("SC_CLAIM_")}
    for lang, keys in locales.items():
        missing = sorted(claim_values - keys)
        if missing:
            errs.append("E2/E3 %s 缺少 %d 个理赔错误码译文：%s"
                        % (lang, len(missing), missing[:8]))

    zh, en = locales["zh-CN"], locales["en-US"]
    if zh - en:
        errs.append("E4 中文包有、英文包无（%d 个）：%s" % (len(zh - en), sorted(zh - en)[:8]))
    if en - zh:
        errs.append("E4 英文包有、中文包无（%d 个）：%s" % (len(en - zh), sorted(en - zh)[:8]))
    return errs


def main():
    if "--self-test" in sys.argv:
        return self_test()
    try:
        codes, registered, locales = load()
    except (OSError, ValueError) as e:
        print("=== 业务错误码双语齐备机检 ===")
        print("【FAIL】读取失败：%s" % e)
        return 2
    errs = verdict(codes, registered, locales)
    print("=== 业务错误码双语齐备机检 ===")
    print("后端常量 %d / HTTP 登记 %d / zh-CN errors %d 键 / en-US errors %d 键"
          % (len(codes), len(registered), len(locales["zh-CN"]), len(locales["en-US"])))
    print("理赔码 SC_CLAIM_* %d 个" % len({v for v in codes.values() if v.startswith("SC_CLAIM_")}))
    if errs:
        for e in errs:
            print("【FAIL】" + e)
        print("共 %d 项失败" % len(errs))
        return 1
    print("【OK】常量↔HTTP 登记双向齐备 + 理赔码双语 100% 覆盖 + 中英键集合零差集")
    return 0


def self_test():
    """反证：全绿不等于有判别力。"""
    codes = {"CLAIM_NOT_FOUND": "SC_CLAIM_NOT_FOUND",
             "CLAIM_STATUS_CONFLICT": "SC_CLAIM_STATUS_CONFLICT"}
    registered = {"CLAIM_NOT_FOUND", "CLAIM_STATUS_CONFLICT"}
    zh = {"SC_CLAIM_NOT_FOUND", "SC_CLAIM_STATUS_CONFLICT", "COMMON_NOT_FOUND"}
    en = {"SC_CLAIM_NOT_FOUND", "SC_CLAIM_STATUS_CONFLICT", "COMMON_NOT_FOUND"}
    locales = {"zh-CN": zh, "en-US": en}
    passed = failed = 0

    def expect(label, cond):
        nonlocal passed, failed
        if cond:
            passed += 1
            print("  ✅ " + label)
        else:
            failed += 1
            print("  ❌ " + label)

    print("=== _check_error_code_i18n.py --self-test ===")
    expect("合规正例", verdict(codes, registered, locales) == [])
    expect("变异① 新增常量漏登记 HTTP → E1 判红",
           any("E1" in e for e in verdict(
               dict(codes, CLAIM_NEW="SC_CLAIM_NEW"), registered, locales)))
    expect("变异② 中文包漏一个译文 → E2/E3 判红",
           any("E2/E3" in e and "zh-CN" in e for e in verdict(
               codes, registered, {"zh-CN": zh - {"SC_CLAIM_NOT_FOUND"}, "en-US": en})))
    expect("变异③ 英文包漏一个译文 → E2/E3 判红",
           any("E2/E3" in e and "en-US" in e for e in verdict(
               codes, registered, {"zh-CN": zh, "en-US": en - {"SC_CLAIM_STATUS_CONFLICT"}})))
    expect("变异④ 中英键集合不一致 → E4 判红",
           any("E4" in e for e in verdict(
               codes, registered, {"zh-CN": zh, "en-US": en - {"COMMON_NOT_FOUND"}})))
    expect("变异⑤ 常量解析为空 → fail-closed",
           any("E5" in e for e in verdict({}, registered, locales)))
    expect("变异⑥ 语言包解析为空 → fail-closed",
           any("E5" in e for e in verdict(
               codes, registered, {"zh-CN": set(), "en-US": en})))
    print("  %d 通过 / %d 失败" % (passed, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
