#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""内部模拟 POC 取证器（诚实、可复现、不篡改历史证据）。

做什么
------
在内部隔离端口栈上，用 tools/internal-poc-provider.py 作为 OpenAI 兼容 provider，
真实调用后端「探活 + 运行期健康度」端点，把**本次实测**证据写入产物的 runtime 段，
再以注入 provider 环境变量的方式跑 L3 就绪度复核器，断言内部模拟 POC 的目标态：

  · 前 3 项纯技术 BLOCKED 全部闭环 → SATISFIED
    （PROVIDER_CONFIGURED / PERCEPTION_MODEL_DRIVEN / RUNTIME_MODEL_COVERAGE）
  · 红线 2 项保持 BLOCKED（CATALOG_ADMISSION_ACTIVE / GOLIVE_NOT_DRAFT）
  · l3Claimable = false，成熟度 L2 —— 不宣称 L3、不宣称可对客出结论
  · 复核器 --self-test 全 PASS

诚实纪律（业界最佳实践）
------------------------
· 不手改任何判据、不碰治理资产 status、不改 application.yml 默认值；
· 探活证据束来自本次真实 HTTP 调用（requestId/model/tokens 原样透传），不编造；
· 历史产物 outputs/claims-e2e-result.json 先备份、验证后逐字恢复，不篡改历史时点；
· provider 由 X-POC-Simulated 头与 x-poc-simulated 字段自证模拟身份；
  本脚本结果只表述为「内部模拟 POC 技术验证」，不构成真实商业 POC 通过。

用法（需 claim=8097 / gateway=18888 / provider=18081 已启动并注入环境变量）：
    python3 scripts/e2e/internal-poc-verify.py \
      --gateway http://127.0.0.1:18888 --token-file /tmp/poc-token \
      --provider-model poc-internal-v1
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ARTIFACT = ROOT / "outputs" / "claims-e2e-result.json"
BACKUP = ROOT / "outputs" / "claims-e2e-result.json.internal-poc-bak"
CHECKER = ROOT / "doc" / "ai-product" / "synapClaim" / "tools" / "_ai_native_readiness.py"

WINDOW_FROM = "2026-10-22T00:00:00Z"
WINDOW_TO = "2026-11-21T00:00:00Z"

REQUIRED_SATISFIED = {
    "PROVIDER_CONFIGURED", "PERCEPTION_MODEL_DRIVEN", "RUNTIME_MODEL_COVERAGE",
}
REQUIRED_BLOCKED = {"CATALOG_ADMISSION_ACTIVE", "GOLIVE_NOT_DRAFT"}


def http_get(url: str, token: str) -> dict:
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=15) as resp:  # noqa: S310 - 本机内部地址
        return json.loads(resp.read().decode("utf-8"))


def parse_checker(out: str) -> tuple[dict[str, str], str, str]:
    """从复核器文本输出解析 {判据: 状态}、成熟度档位、l3Claimable。"""
    gates: dict[str, str] = {}
    level = ""
    claimable = ""
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[0] in ("SATISFIED", "BLOCKED", "RUNTIME_ONLY"):
            gates[parts[1]] = parts[0]
        if "当前可判定成熟度" in line:
            level = line.split("：", 1)[-1].strip()
        if "l3Claimable" in line and "=" in line:
            claimable = line.split("=", 1)[1].strip()
    return gates, level, claimable


def main() -> int:
    ap = argparse.ArgumentParser(description="内部模拟 POC 取证器（诚实取证，非对客结论）")
    ap.add_argument("--gateway", default="http://127.0.0.1:18888")
    ap.add_argument("--token-file", default="/tmp/poc-token")
    ap.add_argument("--provider-base-url", default="http://127.0.0.1:18081/v1")
    ap.add_argument("--provider-model", default="poc-internal-v1")
    args = ap.parse_args()

    token = Path(args.token_file).read_text().strip()
    if not token:
        print("FAIL: token 文件为空，请先登录网关")
        return 1

    # 1) 真实调用后端探活端点 —— 证据来自本次 HTTP 调用，不编造
    probe = http_get(f"{args.gateway}/api/v1/claim-ops/ai-provider-probe", token)
    pdata = probe.get("data") or {}
    print(f"[probe] verdict={pdata.get('verdict')} model={pdata.get('model')} "
          f"provenance={pdata.get('provenance')} requestId={pdata.get('requestId')}")

    # 2) 真实调用运行期健康度端点 —— 参与率证据
    health = http_get(
        f"{args.gateway}/api/v1/claim-ops/ai-runtime-health"
        f"?from={WINDOW_FROM}&to={WINDOW_TO}", token)
    hdata = health.get("data") or {}
    print(f"[health] nodes={hdata.get('nodesScanned')} participated={hdata.get('aiParticipated')} "
          f"rate={hdata.get('participationRate')}")

    # 3) 诚实前置断言：只有本次真实链路证据达标，才继续取证
    assert pdata.get("verdict") == "REACHABLE", "探活非 REACHABLE，内部 POC 不成立"
    assert str(pdata.get("model", "")).lower() == args.provider_model.lower(), \
        "探活模型名非内部 provider，证据来源不纯"
    assert int(hdata.get("aiParticipated") or 0) > 0 and float(hdata.get("participationRate") or 0) > 0, \
        "运行期参与率为 0，模型未上场"

    # 4) 安全更新产物 runtime 段（备份 → 写真实证据 → 跑复核器 → 恢复）
    original_text = ARTIFACT.read_text(encoding="utf-8")
    shutil.copyfile(ARTIFACT, BACKUP)
    try:
        artifact = json.loads(original_text)
        runtime = artifact.setdefault("runtime", {})
        runtime["probeVerdict"] = pdata.get("verdict")
        runtime["probeVerdictCapturedAt"] = datetime.now(timezone.utc).isoformat()
        runtime["probeEvidence"] = {
            "httpStatus": 200,
            "model": pdata.get("model"),
            "provenance": pdata.get("provenance"),
            "promptTokens": pdata.get("promptTokens"),
            "completionTokens": pdata.get("completionTokens"),
            "requestId": pdata.get("requestId"),
        }
        runtime["nodesScanned"] = hdata.get("nodesScanned")
        runtime["aiParticipated"] = hdata.get("aiParticipated")
        runtime["aiDegraded"] = hdata.get("aiDegraded")
        runtime["participationRate"] = hdata.get("participationRate")
        runtime["capabilityMissing"] = hdata.get("capabilityMissing")
        runtime["windowFrom"] = hdata.get("windowFrom")
        runtime["windowTo"] = hdata.get("windowTo")
        artifact["mode"] = "internal-simulated-poc"  # 诚实标注：本次为内部模拟
        ARTIFACT.write_text(json.dumps(artifact, ensure_ascii=False, indent=2), encoding="utf-8")

        # 5) 以注入 provider 环境变量跑复核器（PROVIDER_CONFIGURED 读环境变量）
        env = dict(os.environ)
        env["BONE_AI_GATEWAY_BASE_URL"] = args.provider_base_url
        env["BONE_CLAIM_DOCUMENT_PARSE_MODE"] = "llm"
        result = subprocess.run([sys.executable, str(CHECKER)], capture_output=True, text=True, env=env)
        out = result.stdout
        print("\n--- 复核器输出 ---")
        print(out)
        gates, level, claimable = parse_checker(out)

        # 6) self-test 必须全 PASS（关键的诚实前提）
        # 复核器 load_facts() 读 os.environ 的 provider 变量；self-test 的探针以
        # 「当前 shell 未配 provider」为基线（如『注入 provider → 须翻转』探针）。
        # 故 self-test 必须在剥离 provider 变量的干净环境跑（与 11 号手册 §六一致），
        # 否则基线已被注入成 SATISFIED，翻转/保持类探针会假红。这与主判据是两个
        # 互斥的运行前提：主判据验「配了 provider 的内部 POC 态」，self-test 验判据自身判别力。
        clean_env = dict(env)
        for key in ("BONE_AI_GATEWAY_BASE_URL", "BONE_CLAIM_DOCUMENT_PARSE_MODE",
                    "BONE_AI_GATEWAY_API_KEY", "BONE_AI_GATEWAY_MODEL"):
            clean_env.pop(key, None)
        st = subprocess.run([sys.executable, str(CHECKER), "--self-test"],
                            capture_output=True, text=True, env=clean_env)
        selftest_ok = "自测结果： PASS" in st.stdout
        print(f"[self-test] {'PASS' if selftest_ok else 'FAIL'}")
        if not selftest_ok:
            print(st.stdout[-1500:])

        # 7) 断言内部模拟 POC 目标态
        problems: list[str] = []
        for gid in REQUIRED_SATISFIED:
            if gates.get(gid) != "SATISFIED":
                problems.append(f"{gid} 应为 SATISFIED，实际 {gates.get(gid)}")
        for gid in REQUIRED_BLOCKED:
            if gates.get(gid) != "BLOCKED":
                problems.append(f"{gid} 应保持 BLOCKED（红线），实际 {gates.get(gid)}")
        if claimable.lower() != "false":
            problems.append(f"l3Claimable 应为 false，实际 {claimable}")
        if not level.startswith("L2"):
            problems.append(f"成熟度应为 L2，实际 {level}")
        if not selftest_ok:
            problems.append("复核器 self-test 未全 PASS")

        # 8) 固化本次产物快照（内部模拟证据）供审计
        snapshot = ROOT / "outputs" / "claims-e2e-result.internal-poc.json"
        shutil.copyfile(ARTIFACT, snapshot)
        print(f"\n[snapshot] {snapshot.relative_to(ROOT)}")

        if problems:
            print("\n=== 内部模拟 POC 验证 FAIL ===")
            for p in problems:
                print("  - " + p)
            return 1

        print("\n=== 内部模拟 POC 验证 PASS ===")
        print(f"  技术 BLOCKED 闭环：{', '.join(sorted(REQUIRED_SATISFIED))}")
        print(f"  红线保持：{', '.join(sorted(REQUIRED_BLOCKED))}")
        print(f"  成熟度：{level} / l3Claimable={claimable}（不宣称 L3）")
        print("  边界：内部模拟技术验证，非真实商业 POC，不可对客出结论、不可自动放款")
        return 0
    finally:
        # 9) 无论成败都逐字恢复历史产物，不篡改历史时点
        ARTIFACT.write_text(original_text, encoding="utf-8")
        BACKUP.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
