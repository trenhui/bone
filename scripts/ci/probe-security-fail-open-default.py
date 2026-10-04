#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-security-fail-open-default.py 的负向探针（自证门禁真能抓到违规）。

为什么需要：`static-gate-negative-probe` 的核心纪律——「跑通了」不等于「有效」。
一个只会打印 ✅ 的门禁和一个真能拦人的门禁，在没注入违规样本时输出完全一样。

做法：用 `git show HEAD:<file>` 取**改动前的真实文件**作回归样本（不是手写的假样本），
逐个注入到副本目录，跑门禁断言它**精确失败**并在报错里点名该判据。

三条判据各配一个能证伪它的样本：
  N1 R1（放行不得裸字面量）    ← 基配置改回裸 true
  N2 R2（基配置默认值须false）  ← 基配置占位符默认值改回 true
  N3 R3（生产恒字面量 false）   ← prod profile 改成占位符（留"注入即可放行"的口子）

额外两条元判据（验证脚本自身的可信度）：
  N4 探针必须能抓到**只存在于历史版本**的违规，而不是只认当前磁盘状态
  N5 全部还原后门禁必须回到绿灯（证明探针没留下污染）

用法：python3 scripts/ci/probe-security-fail-open-default.py
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
GATE = REPO / "scripts" / "check-security-fail-open-default.py"
STUDIO = "bone-engine/bone-extension-engine/bone-extension-studio"

BASE_YML = f"{STUDIO}/src/main/resources/application.yml"
INMEM_YML = f"{STUDIO}/src/main/resources/application-in-memory.yml"
PROD_YML = f"{STUDIO}/src/main/resources/application-prod.yml"


def _run_gate(root: Path) -> tuple[int, str]:
    """在 root 下跑门禁（门禁以自身所在位置推导 REPO，故复制整棵相关子树）。"""
    proc = subprocess.run(
        [sys.executable, str(root / "scripts" / GATE.name), "--strict"],
        capture_output=True,
        text=True,
    )
    return proc.returncode, proc.stdout + proc.stderr


def _sandbox(tmp: Path) -> Path:
    """搭一个最小可跑门禁的沙箱：脚本 + 被扫描的 yml 树。"""
    root = tmp / "repo"
    (root / "scripts").mkdir(parents=True)
    shutil.copy2(GATE, root / "scripts" / GATE.name)
    dst_dir = root / STUDIO / "src/main/resources"
    dst_dir.mkdir(parents=True)
    return root


def _stage(root: Path, rel: str, content: str) -> None:
    (root / rel).parent.mkdir(parents=True, exist_ok=True)
    (root / rel).write_text(content, encoding="utf-8")


def _current(rel: str) -> str:
    return (REPO / rel).read_text(encoding="utf-8")


def _from_head(rel: str) -> str | None:
    """取 HEAD 版本（改动前的真实内容）；文件当时不存在则返回 None。"""
    proc = subprocess.run(
        ["git", "show", f"HEAD:{rel}"], cwd=REPO, capture_output=True, text=True
    )
    if proc.returncode != 0:
        return None
    return proc.stdout


def _must_replace(content: str, old: str, new: str, label: str) -> str:
    """替换并断言命中——探针自身不能因为 old 串拼错而静默'注入失败仍绿灯'。"""
    if old not in content:
        raise AssertionError(f"{label}: 待注入片段未命中，无法构造违规样本\n  片段: {old!r}")
    return content.replace(old, new, 1)


def main() -> int:
    failures: list[str] = []
    tmp = Path(tempfile.mkdtemp(prefix="probe-fail-open-"))
    try:
        # ---------- 前置：当前磁盘状态必须是绿的，否则后续断言无意义 ----------
        base_now = _current(BASE_YML)
        if "permit-unauthenticated: ${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:false}" not in base_now:
            failures.append(
                "前置失败：当前基配置已不是失败关闭形态，"
                "负向探针的对照基线不成立（先确认配置收敛是否被回退）"
            )
            print("前置失败：基配置未处于失败关闭形态")
            return 1

        # ---------- N1：基配置改回裸 true ⇒ R1 必须失败 ----------
        root = _sandbox(tmp / "n1")
        bad = _must_replace(
            base_now,
            "permit-unauthenticated: ${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:false}",
            "permit-unauthenticated: true",
            "N1",
        )
        _stage(root, BASE_YML, bad)
        code, out = _run_gate(root)
        if code == 0:
            failures.append("N1 失败：基配置裸 true 未被拦截（门禁形同虚设）")
        elif "R1" not in out:
            failures.append(f"N1 失败：拦截了但未点名 R1 判据，报错不可定位\n{out}")
        else:
            print("✅ N1 R1（裸字面量放行）被精确拦截")

        # ---------- N2：基配置占位符默认值改回 true ⇒ R2 必须失败 ----------
        root = _sandbox(tmp / "n2")
        bad = _must_replace(
            base_now,
            "permit-unauthenticated: ${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:false}",
            "permit-unauthenticated: ${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:true}",
            "N2",
        )
        _stage(root, BASE_YML, bad)
        code, out = _run_gate(root)
        if code == 0:
            failures.append("N2 失败：基配置默认值 true 未被拦截（『忘记配置=全站匿名』无人看守）")
        elif "R2" not in out:
            failures.append(f"N2 失败：拦截了但未点名 R2 判据\n{out}")
        else:
            print("✅ N2 R2（基配置默认值 true）被精确拦截")

        # ---------- N3：prod profile 改成占位符 ⇒ R3 必须失败 ----------
        root = _sandbox(tmp / "n3")
        prod_now = _current(PROD_YML)
        bad = _must_replace(
            prod_now,
            "permit-unauthenticated: false",
            "permit-unauthenticated: ${BONE_EXTENSION_STUDIO_PERMIT_UNAUTHENTICATED:true}",
            "N3",
        )
        _stage(root, PROD_YML, bad)
        code, out = _run_gate(root)
        if code == 0:
            failures.append("N3 失败：生产 profile 的『注入即放行』口子未被拦截")
        elif "R3" not in out:
            failures.append(f"N3 失败：拦截了但未点名 R3 判据\n{out}")
        else:
            print("✅ N3 R3（生产留环境变量放行口子）被精确拦截")

        # ---------- N4：用 HEAD 真实历史样本验证「历史违规会被抓到」 ----------
        head_base = _from_head(BASE_YML)
        if head_base is None:
            failures.append("N4 失败：无法从 HEAD 取到基配置历史版本（探针前提不成立）")
        elif "permit-unauthenticated: true" not in head_base:
            failures.append(
                "N4 失败：HEAD 版基配置里没有裸 true，说明历史样本不含违规，"
                "无法用真实回归样本证伪判据"
            )
        else:
            root = _sandbox(tmp / "n4")
            _stage(root, BASE_YML, head_base)
            code, out = _run_gate(root)
            if code == 0:
                failures.append("N4 失败：改动前的真实历史配置（含裸 true）未被拦截")
            else:
                print("✅ N4 真实历史回归样本（HEAD 裸 true）被拦截")

        # ---------- N5：联调 profile 的合法放行不得被误伤（防误报） ----------
        root = _sandbox(tmp / "n5")
        _stage(root, BASE_YML, base_now)
        _stage(root, INMEM_YML, _current(INMEM_YML))
        _stage(root, PROD_YML, _current(PROD_YML))
        code, out = _run_gate(root)
        if code != 0:
            failures.append(
                f"N5 失败：合法形态（基配置 false + 联调 profile 占位符 true）被误报\n{out}"
            )
        else:
            print("✅ N5 合法形态未被误伤（联调 profile 显式放行仍放行）")

        # ---------- N6：还原后当前仓库必须仍绿（证明探针无污染） ----------
        code, out = _run_gate(REPO)
        if code != 0:
            failures.append(f"N6 失败：探针执行后当前仓库门禁变红，探针污染了工作树\n{out}")
        else:
            print("✅ N6 探针执行后当前仓库仍绿（无污染）")

    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print("\n❌ 负向探针失败：")
        for f in failures:
            print(f"  · {f}")
        return 1
    print("\n✅ 负向探针全部通过：门禁能精确抓到 R1/R2/R3，且不误伤合法形态")
    return 0


if __name__ == "__main__":
    sys.exit(main())
