#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""飞轮下游「强判据」机检：证明校准/模型治理不是靠类名命中的空壳。

为什么需要它
------------
`_ai_native_readiness.py` 的 FLYWHEEL_DOWNSTREAM 只按文件名正则
`(Calibration|Brier|Champion|EceCalibrat)` 命中即判 SATISFIED。新建一个同名空壳类
就能让它翻绿——这是「脚本存在 ≠ 门禁生效」的同型假绿。本脚本逐项验证**代码事实**：

  C1 实现类存在且含全部判据符号（ECE / Brier / 阈值扫描 / challenger / 模型卡 / 漂移）
  C2 每个实现类有对应单测，且用例数有下界（防「一个 test 假装覆盖」）
  C3 防反馈环常量在位（只认 BLIND_LABEL、冻结集隔离、空集给 null）
  C4 单测内含会转红的反向锚点（弱标签拒绝 / 样本不足 / 退化拦截）
  C5 实现类标记为纯领域：不得 import Spring / 持久层 / adapter（零框架依赖）

刻意不做：不读活库、不起服务、不改文件、不判定 L3 达标。
**本脚本只管「内核是否真实且可证伪」，不管「飞轮是否已接线到生产链路」**——
后者需要真实盲标样本，静态事实给不出，绝不因文件存在就宣称闭环。

运行：
  python3 tools/_check_ai_native_calibration.py
  python3 tools/_check_ai_native_calibration.py --self-test
  python3 tools/_check_ai_native_calibration.py --check   # 非零退出（接入门禁时用）
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
MAIN = ROOT / "bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/evaluation"
TEST = ROOT / "bone-apps/synap-claim/src/test/java/com/bone/claim/domain/model/evaluation"

CONTRACTS = [
    ("C1", "AiConfidenceCalibration.java",
     ["expectedCalibrationError", "brierScore", "scanThreshold",
      "challengerAdmissible", "LabelSource.BLIND_LABEL", "frozenSetExcluded"]),
    ("C1", "AiModelCard.java",
     ["INDEPENDENT_VERIFIED", "eligibleForProduction", "inventory", "drift",
      "trainingDataLineage", "retirementCondition"]),
]

TEST_CASES = [
    ("C2", "AiConfidenceCalibrationTest.java", 12),
    ("C2", "AiModelCardTest.java", 6),
]

GUARDS = [
    ("C3", "AiConfidenceCalibration.java", "return null;"),
    ("C3", "AiConfidenceCalibration.java", "UNTRUSTED_EVIDENCE"),
    ("C3", "AiConfidenceCalibration.java", "INSUFFICIENT_SAMPLES"),
    ("C4", "AiConfidenceCalibrationTest.java", "WEAK_FINAL_STATUS"),
    ("C4", "AiConfidenceCalibrationTest.java", "AI_SELF_EVALUATION"),
    ("C4", "AiConfidenceCalibrationTest.java", "sliceRegressionBlocksPromotion"),
]

FRAMEWORK_IMPORTS = re.compile(
    r"^\s*import\s+(org\.springframework|jakarta\.|com\.bone\.metadata|"
    r"com\.bone\.claim\.(application|adapter|infrastructure))\b", re.M)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def violations(root: Path) -> list[str]:
    """root 参数用于 --self-test 注入变造后的目录，防止判据恒真。"""
    main = root / "bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/evaluation"
    test = root / "bone-apps/synap-claim/src/test/java/com/bone/claim/domain/model/evaluation"
    out: list[str] = []

    for cid, name, symbols in CONTRACTS:
        text = read(main / name)
        if not text:
            out.append(f"{cid} 实现类缺失：{name}")
            continue
        for sym in symbols:
            if sym not in text:
                out.append(f"{cid} 判据符号缺失：{name} :: {sym}")
        if FRAMEWORK_IMPORTS.search(text):
            out.append(f"C5 纯领域内核出现框架/外层依赖：{name}")

    for cid, name, minimum in TEST_CASES:
        text = read(test / name)
        if not text:
            out.append(f"{cid} 单测缺失：{name}")
            continue
        count = len(re.findall(r"^\s*@Test\b", text, re.M))
        if count < minimum:
            out.append(f"{cid} 单测用例数 {count} < 下界 {minimum}：{name}")

    for cid, name, anchor in GUARDS:
        base = main if name.endswith("Calibration.java") and not name.endswith("Test.java") else test
        if name.endswith("Test.java"):
            base = test
        text = read(base / name)
        if anchor not in text:
            out.append(f"{cid} 防假绿锚点缺失：{name} :: {anchor}")
    return out


def self_test() -> bool:
    """负向探针：把实现类换成空壳 / 删单测，对应判据必须报违规。"""
    import shutil
    import tempfile

    ok = True
    probes = [
        ("校准内核换成空类", MAIN / "AiConfidenceCalibration.java",
         "package com.bone.claim.domain.model.evaluation;\n"
         "public final class AiConfidenceCalibration {}\n", "C1"),
        ("模型卡换成空类", MAIN / "AiModelCard.java",
         "package com.bone.claim.domain.model.evaluation;\n"
         "public final class AiModelCard {}\n", "C1"),
        ("删除校准单测", TEST / "AiConfidenceCalibrationTest.java", "", "C2"),
        ("校准内核引入 Spring 依赖", MAIN / "AiConfidenceCalibration.java",
         "package com.bone.claim.domain.model.evaluation;\n"
         "import org.springframework.stereotype.Service;\n"
         "public final class AiConfidenceCalibration {}\n", "C5"),
    ]
    for label, rel_target, replacement, expected in probes:
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            (tmp / "bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/evaluation").mkdir(parents=True)
            (tmp / "bone-apps/synap-claim/src/test/java/com/bone/claim/domain/model/evaluation").mkdir(parents=True)
            for p in MAIN.glob("*.java"):
                shutil.copy(p, tmp / "bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/evaluation" / p.name)
            for p in TEST.glob("*.java"):
                shutil.copy(p, tmp / "bone-apps/synap-claim/src/test/java/com/bone/claim/domain/model/evaluation" / p.name)
            target = tmp / rel_target.relative_to(ROOT)
            if replacement:
                target.write_text(replacement, encoding="utf-8")
            else:
                target.unlink()
            found = any(v.startswith(expected) for v in violations(tmp))
            print(f"  [{'OK' if found else 'FAIL'}] {label} ⇒ 期望 {expected} 违规")
            ok = ok and found
    return ok


def main() -> int:
    if "--self-test" in sys.argv:
        print("负向探针（判据必须报违规，否则判据恒真=假绿）：")
        passed = self_test()
        print("自测结果：", "PASS" if passed else "FAIL")
        return 0 if passed else 1

    bad = violations(ROOT)
    if bad:
        for v in bad:
            print("  [VIOLATION]", v)
        print(f"飞轮下游强判据：FAIL（{len(bad)} 项）")
        return 1 if "--check" in sys.argv else 0
    print("飞轮下游强判据：PASS（实现类 / 单测 / 防假绿锚点 / 纯领域依赖 均就位）")
    print("注意：本脚本不判定『飞轮已接线到生产链路』——那需要真实盲标样本，静态事实给不出。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
