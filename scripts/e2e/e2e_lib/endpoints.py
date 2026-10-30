# -*- coding: utf-8 -*-
"""端点清单加载（唯一真源 = Controller 源码，不是 /tmp 缓存）。

【为什么要有这一层】2026-10-30 全模块联调实测确诊：api-sweep / write-sweep / real-id-sweep
三个脚本此前都直接 `json.load(open("/tmp/endpoints.json"))`，而该文件是**某次手工跑
_extract_endpoints.py 留下的 4 天前的快照**（403 条，缺 bone-workflow-engine 整个模块）。
后果是典型的「假绿」：扫描报告「0 个 5xx」，但新增/改名的端点根本没被探测过——
报告里的分母（158/178）本身就是过期的，结论完全失真。

因此统一策略：**每次扫描都从源码重新提取**，/tmp/endpoints.json 只作为其他手工工具的
兼容落点（顺手刷新），绝不作为扫描输入。
"""
import json
import os
import subprocess
import sys

# .../scripts/e2e/e2e_lib/endpoints.py → 上溯 4 层才是仓库根（少一层会得到 <repo>/scripts）
REPO_ROOT = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
)
EXTRACTOR = os.path.join(REPO_ROOT, "scripts", "e2e", "_extract_endpoints.py")
CACHE = "/tmp/endpoints.json"


def load(fail_on_empty: bool = True) -> list:
    """从 Controller 源码重新提取全量端点。

    返回 [{module, controller, method, http, path, has_path_var}]。
    提取失败（返回非 JSON / 空）时按 fail_on_empty 抛错——宁可扫描红，不可静默跳过。
    """
    py = sys.executable or "python3"
    p = subprocess.run(
        [py, EXTRACTOR],
        cwd=REPO_ROOT,  # 提取器用相对 ROOTS 扫描，必须在仓库根执行
        capture_output=True,
        text=True,
        timeout=180,
    )
    try:
        eps = json.loads(p.stdout)
    except (ValueError, TypeError):
        eps = None
    if not isinstance(eps, list) or (not eps and fail_on_empty):
        raise RuntimeError(
            "端点提取失败（stdout 非 JSON 或为空）：rc=%s stderr=%s"
            % (p.returncode, (p.stderr or "")[:400])
        )
    # 顺手刷新兼容落点，避免手工工具继续读到旧快照
    try:
        with open(CACHE, "w", encoding="utf-8") as f:
            json.dump(eps, f, ensure_ascii=False, indent=1)
    except OSError:
        pass
    return eps
