#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G0 度量与契约基线门禁：把「文档里的准入判据」变成会红的 CI 约束。

背景（为什么需要这条门禁）
--------------------------
`AI技术方案.md` §11.3 / §12.2 与复核报告 §7.3 把以下三项写成 **AI 原生准入判据**：
  1. 非 ML 基线 + 切片指标进入 CI；
  2. 冻结集哈希可校验、任一样本改动即拒绝执行；
  3. 冻结集永不参与训练与调优（防 Hidden Feedback Loops）。
但此前三者**只存在于文档**：`bone-ai-rag/eval/` 无冻结集、无哈希、无切片定义、
无数据说明书，ci.yml 也从未调用 eval sidecar。文档说「必须有」，代码里没有人看着，
于是「改了文档就算完成」——这正是复核报告 §八 指出的「缺的是机检护栏」。

本门禁钉死的是**结构真源与状态诚实性**，不编造任何实测指标：
  E1 骨架清单存在且通过 frozen_set.verify_manifest（V0..V10 全部判据）
  E2 frozen_set.self_test() 必须通过（防止有人把判据改成恒绿后 CI 依旧绿）
  E3 指标字典结构完整（6 项基线 + 字段齐全），条目数有下界（防「删条目让门禁变轻」）
  E4 切片维度单一真源：字典 sliceDimensions == frozen_set.SLICE_DIMENSIONS，
     且每项指标的 slices 覆盖全部五维（总体指标不得掩盖小样本切片）
  E5 状态自洽 / 防文档抢跑：skeleton 态不允许任何 metric 是 wired；
     declared 必须 sourceSymbol=null 且 blockingReason 非空；
     wired 必须 sourceSymbol 非空且 blockingReason 为空
  E6 wired 符号必须在**真实源码**中有类/接口/record/enum 声明（防「字典说已接线，
     代码里没有」；用 finditer 全量扫 + 计数对账，不用 search 只取首个匹配）
  E7 防假绿：扫描不到 .java 源码 / 声明命中数为 0 / 字典条目为 0 ⇒ 判失败
     （路径失效导致门禁恒绿，比没有门禁更危险）
  E8 wiredObservability 每项都必须能在源码中定位声明，且 id 不重复

刻意**不做**的事：不校验准确率 / 自动化率数值、不要求冻结集已采集 300 条、
不要求 provider 已配置。这些依赖业务口径与真实样本（报告 §九 第 2/3/6 条），
在门禁里硬编码阈值只会逼出假数据。

用法:
  python3 scripts/check-eval-baseline.py --check     # CI blocking
  python3 scripts/check-eval-baseline.py             # 人类可读报告
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

REPO = Path(__file__).resolve().parent.parent
EVAL_REL = Path("bone-ai/bone-ai-rag/eval")
FROZEN_SET_REL = EVAL_REL / "frozen_set.skeleton.json"
METRICS_REL = EVAL_REL / "baseline-metrics.json"
ENGINE_REL = EVAL_REL / "frozen_set.py"

# 报告 §7.3 G0-1 的四项基线 + 新增单案 AI 成本 + 人工复核率（评测集口径）
EXPECTED_METRIC_IDS: Tuple[str, ...] = (
    "manual_case_minutes",
    "field_extraction_accuracy",
    "adjustment_accuracy",
    "leakage_rate",
    "ai_cost_per_case",
    "manual_review_rate",
)
METRIC_FIELDS: Tuple[str, ...] = (
    "id", "name", "definition", "unit", "targetRef",
    "slices", "status", "sourceSymbol", "blockingReason",
)
# 源码扫描范围：只扫真实代码树，排除构建产物与前端依赖
SCAN_ROOTS: Tuple[str, ...] = ("bone-apps", "bone-ai")
DECL_RE_TEMPLATE = r"\b(?:class|interface|record|enum)\s+{name}\b"


def load_engine(root: Path):
    """按路径加载 frozen_set.py（eval sidecar 不是可导入包）。"""
    path = root / ENGINE_REL
    spec = importlib.util.spec_from_file_location("bone_g0_frozen_set", path)
    if spec is None or spec.loader is None:
        raise ImportError(f"无法加载 {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def read_json(path: Path, rule: str) -> Tuple[Any, List[str]]:
    """按调用方标注规则 ID，保证错误落在可运维的判据上。"""
    if not path.is_file():
        return None, [f"{rule}: 找不到真源文件 {path}"]
    try:
        return json.loads(path.read_text(encoding="utf-8")), []
    except (OSError, ValueError) as exc:
        return None, [f"{rule}: 真源文件解析失败 {path.name}: {exc}"]


def java_sources(root: Path) -> List[Path]:
    """真实源码树中的 .java 文件（排除 target / node_modules / 测试夹具目录）。"""
    out: List[Path] = []
    for sub in SCAN_ROOTS:
        base = root / sub
        if not base.is_dir():
            continue
        for path in base.rglob("*.java"):
            parts = path.parts
            if "target" in parts or "node_modules" in parts:
                continue
            out.append(path)
    return sorted(out)


def declaration_hits(sources: List[Path], symbol: str) -> int:
    """用 finditer 统计符号在**全量源码**中的声明次数（search 只取首个会静默漏扫）。"""
    pattern = re.compile(DECL_RE_TEMPLATE.format(name=re.escape(symbol)))
    total = 0
    for path in sources:
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        total += sum(1 for _ in pattern.finditer(text))
    return total


def check_metrics(doc: Any, engine, sources: List[Path]) -> List[str]:
    errors: List[str] = []
    if not isinstance(doc, dict):
        return ["E3: 指标字典必须为 JSON 对象"]
    if doc.get("schemaVersion") != 1:
        errors.append("E3: 指标字典 schemaVersion 必须为 1")

    dims = doc.get("sliceDimensions")
    expected_dims = list(engine.SLICE_DIMENSIONS)
    if dims != expected_dims:
        errors.append(f"E4: sliceDimensions 必须与冻结集单一真源一致：{expected_dims}")

    metrics = doc.get("metrics")
    if not isinstance(metrics, list) or not metrics:
        return errors + ["E3: metrics 必须为非空数组（没有指标等于没有基线）"]
    if len(metrics) < len(EXPECTED_METRIC_IDS):
        errors.append(
            f"E3: 指标条目 {len(metrics)} 项 < 下界 {len(EXPECTED_METRIC_IDS)}；"
            "禁止通过删条目让门禁变轻"
        )

    ids = [m.get("id") for m in metrics if isinstance(m, dict)]
    for expected in EXPECTED_METRIC_IDS:
        if expected not in ids:
            errors.append(f"E3: 缺少基线指标 {expected}（G0-1 口径）")
    if len(ids) != len(set(ids)):
        errors.append("E3: 指标 id 重复")

    skeleton = doc.get("status") == "skeleton"
    for idx, metric in enumerate(metrics):
        tag = f"metrics[{idx}]"
        if not isinstance(metric, dict):
            errors.append(f"E3: {tag} 必须为对象")
            continue
        missing = [f for f in METRIC_FIELDS if f not in metric]
        if missing:
            errors.append(f"E3: {tag} 缺少字段 {', '.join(missing)}")
        slices = metric.get("slices")
        if slices != expected_dims:
            errors.append(
                f"E4: {tag}.slices 必须覆盖全部五维且顺序一致（防止只看总体指标）"
            )
        status = metric.get("status")
        sym = metric.get("sourceSymbol")
        reason = metric.get("blockingReason")
        if status not in ("declared", "wired"):
            errors.append(f"E5: {tag}.status 必须为 declared|wired")
        elif status == "declared":
            if sym is not None:
                errors.append(f"E5: {tag} 声明 declared 却给了 sourceSymbol（状态不自洽）")
            if not isinstance(reason, str) or not reason.strip():
                errors.append(f"E5: {tag} 声明 declared 必须写明 blockingReason（为何未接线）")
        else:
            if skeleton:
                errors.append(
                    f"E5: {tag} skeleton 态不允许 wired；未采集冻结集却宣称已接线＝文档抢跑"
                )
            if not isinstance(sym, str) or not sym.strip():
                errors.append(f"E5: {tag} 声明 wired 必须给出 sourceSymbol")
            elif declaration_hits(sources, sym) == 0:
                errors.append(
                    f"E6: {tag}.sourceSymbol={sym!r} 在真实源码中找不到声明；"
                    "字典不得抢跑于代码"
                )
            if reason not in (None, ""):
                errors.append(f"E5: {tag} 已 wired 却仍写 blockingReason，状态自相矛盾")
        for field in ("name", "definition", "unit", "targetRef"):
            if not isinstance(metric.get(field), str) or not metric[field].strip():
                errors.append(f"E3: {tag}.{field} 必须非空（口径不可省略）")

    wired = doc.get("wiredObservability")
    if not isinstance(wired, list) or not wired:
        errors.append("E8: wiredObservability 必须为非空数组（可复用挂载点要有清单）")
    else:
        wids = [w.get("id") for w in wired if isinstance(w, dict)]
        if len(wids) != len(set(wids)):
            errors.append("E8: wiredObservability.id 重复")
        for idx, item in enumerate(wired):
            tag = f"wiredObservability[{idx}]"
            if not isinstance(item, dict):
                errors.append(f"E8: {tag} 必须为对象")
                continue
            if item.get("status") != "wired":
                errors.append(f"E8: {tag}.status 必须为 wired")
            sym = item.get("sourceSymbol")
            if not isinstance(sym, str) or not sym.strip():
                errors.append(f"E8: {tag}.sourceSymbol 必须非空")
            elif declaration_hits(sources, sym) == 0:
                errors.append(
                    f"E6: {tag}.sourceSymbol={sym!r} 在真实源码中找不到声明"
                    "（埋点符号漂移或已被删除）"
                )
    return errors


def check(root: Path) -> List[str]:
    """纯函数：返回违规列表（空表示通过）。单测与 CI 共用，main 只做编排。"""
    root = Path(root)
    try:
        engine = load_engine(root)
    except (ImportError, OSError, SyntaxError) as exc:
        return [f"E2: 无法加载冻结集引擎 {ENGINE_REL}：{exc}"]

    ok, failures = engine.self_test()
    if not ok:
        return ["E2: frozen_set 判据自证失败（判据可能已退化成恒绿）：" + "; ".join(failures)]

    # E2b 独立哨兵：门禁不能只问「引擎你自证通过了吗」——把 self_test 改成恒返回
    # (True, []) 就能让 E2 失效。这里由门禁自己造已知违规清单，独立断言判据非空且
    # 精确归因，任何「verify_manifest 恒返回 []」的退化都会被当场抓住。
    probes = (
        ("V3", {"frozenHash": "0" * 64}),
        ("V4", {"collected": 300}),
    )
    for rule, patch in probes:
        sentinel = engine.skeleton_manifest()
        sentinel.update(patch)
        sentinel_errors = engine.verify_manifest(sentinel)
        if not any(e.startswith(rule + ":") for e in sentinel_errors):
            return [f"E2: 独立哨兵未命中 {rule}；冻结集判据可能已退化成恒绿"]

    manifest, errs = read_json(root / FROZEN_SET_REL, "E1")
    metrics_doc, errs2 = read_json(root / METRICS_REL, "E3")
    errors = errs + errs2
    if manifest is None or metrics_doc is None:
        return errors

    errors.extend("E1: " + e for e in engine.verify_manifest(manifest))

    sources = java_sources(root)
    decl_count = 0
    for path in sources:
        text = path.read_text(encoding="utf-8", errors="ignore")
        decl_count += sum(1 for _ in re.finditer(r"\b(?:class|interface|record|enum)\s+\w+", text))
    if not sources or decl_count == 0:
        errors.append(
            "E7: 源码扫描命中 0 个 .java / 0 个类型声明；路径失效会让 E6 恒绿，必须判失败"
        )

    errors.extend(check_metrics(metrics_doc, engine, sources))
    return errors


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="G0 度量与契约基线门禁")
    ap.add_argument("--check", action="store_true", help="CI 模式：有违规即非零退出")
    ap.add_argument("--root", default=str(REPO), help="仓库根（单测注入用）")
    args = ap.parse_args(argv)

    errors = check(Path(args.root))
    if errors:
        print(f"❌ G0 基线门禁：{len(errors)} 项不合规")
        for e in errors:
            print("  · " + e)
        return 1
    print(
        "OK: G0 基线门禁通过（冻结集骨架 + 6 项非 ML 基线指标 + 切片五维 + "
        "wired 符号源码可达 + 判据自证）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
