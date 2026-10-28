# -*- coding: utf-8 -*-
"""运行级残留 teardown：退出（含异常 / sys.exit）时自动回收本次运行的库内残留。

为什么需要：E2E 每轮都会在库里留下带 <SUFFIX> 的实体/物理表/记录/工作流行/临时账号，
反复联调不清理会把库越堆越脏（历史上曾需手工清残留才能复跑 95_validate）。

约定：
  * `register(suffix, ...)` 在脚本**模块级**调用一次，注册 atexit 回调；
  * `BONE_E2E_KEEP_RESIDUE=1` 跳过清理（保留现场供 UI 探针消费 / 排查），
    因此接续跑 UI 探针前要带该变量启动全链路脚本，探针消费完再手工补清理：
    `python3 scripts/e2e/cleanup-residue.py --suffix <sfx> --apply`；
  * 执行体是 scripts/e2e/cleanup-residue.py（带 BONE_ENV=prod 护栏、后缀校验、
    平台基线账号保护），本模块只负责编排与输出；
  * Python 3.14 的 atexit 回调里 `__file__` 不可见 ⇒ 路径在模块级预解析成全局常量。
"""
import atexit
import os
import subprocess
import sys

from . import config

# 模块级预解析（勿移入回调函数体内，见模块 docstring）
_CLEANER = config.CLEANER


def clean(suffix, accounts=(), on_clean=None, label="teardown"):
    """执行一次残留清理。返回 True=已清理或已按策略保留，False=清理失败。

    `accounts`：本次运行创建的临时账号（逐个 `--account` 传给 cleaner）；
    `on_clean`：清理成功后的回调（如删除过期的 state 交接文件）。
    """
    if os.environ.get("BONE_E2E_KEEP_RESIDUE") == "1":
        acc = f", account={','.join(accounts)}" if accounts else ""
        print(f"\n[{label}] 保留本次残留（suffix={suffix}{acc}）供 UI 探针/排查")
        return True
    cmd = [sys.executable, _CLEANER, "--suffix", str(suffix), "--apply"]
    for a in accounts:
        cmd += ["--account", a]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=180)
        if r.returncode == 0:
            print(f"\n[{label}] 残留已清理（suffix={suffix}）")
            for line in (r.stdout or "").strip().splitlines()[-6:]:
                print("            " + line)
            if on_clean:
                try:
                    on_clean()
                except Exception as e:  # 回调失败不该污染已通过的测试结果
                    print(f"[{label}] 清理后回调异常（不影响测试结果）: {e}")
            return True
        print(f"\n[{label}] 清理失败 rc={r.returncode}: {(r.stderr or '')[:300]}")
        return False
    except Exception as e:
        print(f"\n[{label}] 清理异常（不影响测试结果）: {e}")
        return False


def register(suffix, accounts=(), on_clean=None):
    """注册退出清理（脚本模块级调用一次）。"""
    atexit.register(clean, suffix, accounts=accounts, on_clean=on_clean)
