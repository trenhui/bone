#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG 评测脚手架（诊断 R5：评测门禁前置）。

独立于业务进程运行（sidecar），只对「检索结果 vs 金标准」做可量化打分，不依赖任何业务运行时。
用于：
  1. 标定 minScore / MMR / candidate-pool 等阈值（不再裸奔上线）；
  2. 回归门禁——CI 中 context_recall 低于阈值即失败。

用法:
  python eval_rag.py --golden golden_set.sample.json --actual actual_retrieve.json [--recall-gate 0.8]

golden_set.sample.json 形如:
  {
    "dataset": "contract-clause-sample",
    "cases": [
      {"query": "甲方逾期付款的违约金怎么算", "docScope": "doc-1",
       "expectedChunkRefs": ["1", "3"], "topK": 5, "minScore": 0.0}
    ]
  }

actual_retrieve.json 形如（与 RagRetrieveOutcome 对齐，每 case 一行或整体数组）:
  [
    {"query": "甲方逾期付款的违约金怎么算", "docScope": "doc-1",
     "results": [{"chunkRef": "1", "clauseRef": "..."}, {"chunkRef": "3", ...}],
     "degradations": []}
  ]

指标（逐 case，再聚合）:
  - context_recall     = |命中预期 ∩ 实际返回| / |预期总数|
  - context_precision  = |命中预期 ∩ 实际返回| / |实际返回数|（实际为空则记 0）
  - degradation_rate   = 发生降级（degradations 非空）的 case 占比

faithfulness（忠实度：生成答案是否仅由上下文支撑）需 LLM 判定，留接入点 TODO，
此处不实现以免引入未验证依赖；可后续接网关 grounding 守卫的判定结果回填。
"""
from __future__ import annotations

import argparse
import json
import sys
from typing import Any, Dict, List


def load_json(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def normalize_actual(actual: Any) -> Dict[str, Dict[str, Any]]:
    """把 actual 归一成 {case_key: {"results":[chunkRef...], "degradations":[...]}}。

    case_key = (query, docScope) 元组字符串，用于与 golden 对齐。
    """
    if isinstance(actual, dict):
        actual = actual.get("cases", [])
    out: Dict[str, Dict[str, Any]] = {}
    for item in actual:
        key = _case_key(item.get("query"), item.get("docScope"))
        results = [r.get("chunkRef") for r in item.get("results", []) if r.get("chunkRef")]
        out[key] = {
            "results": results,
            "degradations": item.get("degradations", []),
        }
    return out


def _case_key(query: str | None, doc_scope: str | None) -> str:
    return f"{query}\u0001{doc_scope if doc_scope is not None else ''}"


def evaluate(golden: Dict[str, Any], actual_map: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    cases = golden.get("cases", [])
    recalls: List[float] = []
    precisions: List[float] = []
    degraded = 0
    per_case: List[Dict[str, Any]] = []

    for c in cases:
        key = _case_key(c.get("query"), c.get("docScope"))
        expected = set(str(x) for x in c.get("expectedChunkRefs", []))
        actual = actual_map.get(key, {"results": [], "degradations": []})
        got = set(str(x) for x in actual["results"])

        hit = expected & got
        rec = (len(hit) / len(expected)) if expected else 1.0
        prec = (len(hit) / len(got)) if got else 0.0
        recalls.append(rec)
        precisions.append(prec)
        if actual["degradations"]:
            degraded += 1

        per_case.append(
            {
                "query": c.get("query"),
                "docScope": c.get("docScope"),
                "expected": sorted(expected),
                "got": sorted(got),
                "hit": sorted(hit),
                "context_recall": round(rec, 4),
                "context_precision": round(prec, 4),
                "degradations": actual["degradations"],
            }
        )

    n = len(cases)
    agg = {
        "dataset": golden.get("dataset", "unknown"),
        "cases": n,
        "mean_context_recall": round(sum(recalls) / n, 4) if n else 0.0,
        "mean_context_precision": round(sum(precisions) / n, 4) if n else 0.0,
        "degradation_rate": round(degraded / n, 4) if n else 0.0,
        "per_case": per_case,
    }
    return agg


def main(argv: List[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="RAG 检索评测（golden vs actual）")
    parser.add_argument("--golden", required=True, help="金标准集 JSON 路径")
    parser.add_argument("--actual", required=True, help="实际检索结果 JSON 路径（与 RagRetrieveOutcome 对齐）")
    parser.add_argument("--recall-gate", type=float, default=0.8, help="context_recall 聚合门槛，低于则非零退出")
    parser.add_argument("--out", default=None, help="评测报告输出路径（默认打印到 stdout）")
    args = parser.parse_args(argv)

    golden = load_json(args.golden)
    actual = load_json(args.actual)
    actual_map = normalize_actual(actual)
    report = evaluate(golden, actual_map)

    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        print(f"[eval] report written to {args.out}")
    else:
        print(text)

    if report["mean_context_recall"] < args.recall_gate:
        print(
            f"[eval] FAIL: mean_context_recall={report['mean_context_recall']} < gate={args.recall_gate}",
            file=sys.stderr,
        )
        return 2
    print(f"[eval] PASS: mean_context_recall={report['mean_context_recall']} >= gate={args.recall_gate}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
