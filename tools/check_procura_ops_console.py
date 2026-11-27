#!/usr/bin/env python3
"""procuraAI 采购运营工作台门禁（静态结构 + 注入必红探针）。

守护三件事：
1. 三条新路由已注册且指向正确页面；
2. 请求体不含前端自报身份（tenantId/operator/confirmedBy/reviewer）；
3. 失败态 fail-closed（success=false 判失败、未知码保留原始码、PII 白名单）。

--self-test 对临时副本做注入，验证每条判据都能转红（防假绿）。
"""
from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "bone-frontend/apps/bone-procurement-app/src"

APP_TSX = ROOT / "bone-frontend/apps/bone-procurement-app/src/App.tsx"
SOURCING_API = APP / "services/sourcingApi.ts"
RISK_API = APP / "services/supplierRiskApi.ts"
SOURCING_VIEW = APP / "types/sourcingView.ts"
RISK_VIEW = APP / "types/supplierRiskView.ts"
TASKS = ROOT / "openspec/changes/procuraai-procurement-ops-console/tasks.md"

ROUTES = [
    ("/sourcing", "SourcingConsole"),
    ("/supplier-onboarding", "SupplierOnboarding"),
    ("/supplier-risk", "SupplierRisk"),
]


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def check_routes(app: str) -> list[str]:
    errors: list[str] = []
    for path, comp in ROUTES:
        if not re.search(rf'<Route\s+path="{re.escape(path)}"\s+element=\{{<{comp}\s*/>', app):
            errors.append(f"路由 {path} 未指向 {comp}")
        if f"import {comp} from './pages/{comp}'" not in app:
            errors.append(f"App.tsx 未导入 {comp}")
    for legacy in ("/transactions", "/suppliers", "/fulfillment", "/contract-hitl", "/governance"):
        if f'path="{legacy}"' not in app:
            errors.append(f"既有路由 {legacy} 被移除")
    return errors


def check_identity(source: str, label: str) -> list[str]:
    """请求体不得含自报身份字段；只查 post 的 payload 构造，不查响应 DTO 字段。"""
    errors: list[str] = []
    # 抽取每个 apiClient.post(url, X) 的第二个实参做检查
    for m in re.finditer(r"apiClient\.post\([^,]+,\s*([^)]+)\)", source, re.S):
        body = m.group(1)
        for field in ("tenantId", "operator", "confirmedBy", "reviewer"):
            if re.search(rf"\b{field}\s*:", body):
                errors.append(f"{label} 请求体含自报身份字段 {field}")
    return errors


def check_fail_closed(sourcing: str, risk: str, sview: str, rview: str) -> list[str]:
    errors: list[str] = []
    for label, src in (("sourcingApi", sourcing), ("supplierRiskApi", risk)):
        if "env.success === false" not in src:
            errors.append(f"{label} 未把 success=false 判为失败")
        if "degradedReason" not in src:
            errors.append(f"{label} 失败态缺降级原因")
    if "原始码" not in sview or "无法确认" not in sview:
        errors.append("sourcingView 未知状态未保留原始码/无法确认")
    if "原始码" not in rview or "无风险" not in rview:
        errors.append("supplierRiskView 未知严重度未保留原始码或未防无风险")
    if "stripPii" not in rview or not re.search(
        r"const\s+ALLOWED\s*=\s*new\s+Set\(\[", rview
    ):
        errors.append("supplierRiskView 缺固定 PII 白名单")
    elif "Object.keys" in rview.split("const ALLOWED")[1].split(";")[0]:
        errors.append("supplierRiskView PII 白名单被改成动态全量（假绿）")
    return errors


def run_checks(base: Path) -> list[str]:
    app = read(base / APP_TSX.relative_to(ROOT))
    sourcing = read(base / SOURCING_API.relative_to(ROOT))
    risk = read(base / RISK_API.relative_to(ROOT))
    sview = read(base / SOURCING_VIEW.relative_to(ROOT))
    rview = read(base / RISK_VIEW.relative_to(ROOT))
    errors: list[str] = []
    errors += check_routes(app)
    errors += check_identity(sourcing, "sourcingApi")
    errors += check_identity(risk, "supplierRiskApi")
    errors += check_fail_closed(sourcing, risk, sview, rview)
    tasks = read(base / TASKS.relative_to(ROOT))
    checked = len(re.findall(r"^- \[x\]", tasks, re.M))
    if checked < 11:
        errors.append(f"tasks.md 勾选 {checked}/11，未满")
    return errors


def self_test() -> int:
    """每条判据注入临时副本，必须转红；原文件零改动。"""
    probes = [
        ("路由删除", lambda s: s.replace('path="/sourcing" element={<SourcingConsole />}', ''), None, None),
        ("自报身份注入", None, "sourcingApi", lambda s: s.replace(
            "await apiClient.post(`${SOURCING_BASE}/bids`, payload)",
            "await apiClient.post(`${SOURCING_BASE}/bids`, { ...payload, tenantId: 1 })")),
        ("success=false 判据移除", None, "sourcingApi", lambda s: s.replace(
            "env.success === false", "false")),
        ("PII 白名单移除", None, "supplierRiskView", lambda s: s.replace(
            "const ALLOWED = new Set(['supplierId', 'displayName', 'score', 'grade', 'period']);",
            "const ALLOWED = new Set(Object.keys(raw ?? {}));")),
    ]
    failed = 0
    for name, app_mut, file_key, src_mut in probes:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            # 复制整个 src 的相对结构
            for p in (APP_TSX, SOURCING_API, RISK_API, SOURCING_VIEW, RISK_VIEW, TASKS):
                rel = p.relative_to(ROOT)
                dst = tmp / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                dst.write_text(read(p), encoding="utf-8")
            if app_mut:
                p = tmp / APP_TSX.relative_to(ROOT)
                p.write_text(app_mut(read(p)), encoding="utf-8")
            if src_mut:
                target = {"sourcingApi": SOURCING_API, "supplierRiskView": RISK_VIEW}[file_key]
                p = tmp / target.relative_to(ROOT)
                mutated = src_mut(read(p))
                if mutated == read(p):
                    print(f"[FAIL] 探针「{name}」注入锚点未命中")
                    failed += 1
                    continue
                p.write_text(mutated, encoding="utf-8")
            errors = run_checks(tmp)
            if errors:
                print(f"[PASS] 探针「{name}」转红：{errors[0][:60]}")
            else:
                print(f"[FAIL] 探针「{name}」未转红（假绿）")
                failed += 1
    return failed


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        n = self_test()
        print(f"[SELF-TEST] {'ALL PASS' if n == 0 else f'{n} FAILED'}")
        return 1 if n else 0
    errors = run_checks(ROOT)
    if errors:
        print("[FAIL] procuraAI 运营工作台门禁未通过：")
        for e in errors:
            print(f"  - {e}")
        return 1
    print("[PASS] procuraAI 运营工作台门禁通过（路由/身份边界/失败态/PII/任务勾选）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
