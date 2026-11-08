#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""L3 就绪度复核器：4 条「证据驱动」判据的表征测试（2026-11-07 循环复核）。

背景（为什么需要独立文件，而不只靠 --self-test）
------------------------------------------------
`_ai_native_readiness.py --self-test` 只在**内存 facts** 上变造，能证明
「判据函数有判别力」，但证明不了 `load_facts()` 是否真的读到正确的事实真源。
本轮取证发现 4 条判据可被极低成本刷绿：

  · CATALOG_ADMISSION_ACTIVE 读的是模块内 V15（0090 头部原文明确：V1..V15
    是**从未被 runner 执行的孤儿通道**）；删掉 V15 反而判绿。且生效通道
    默认 NONE 时生产没有 `ItemCatalogPort` 实现，强推 MANDATORY 只会把
    「静默超赔」换成「全量退补件停摆」。
  · BASELINE_WIRED 只读 6 个 `status` 字符串；而 baseline-metrics.json
    顶层是 `skeleton`、每项 `sourceSymbol=null`、无 provenance、无采集器。
  · FROZEN_EVALSET_ACTIVE 读 evalset 的 `status=active` + 任意非空
    `payloadHash`；但 check-claim-ai-evalset.py A1 只允许 declared|wired，
    两套 status 枚举打架，且 `"aaa"` 也能算哈希。
  · GOLIVE_NOT_DRAFT 只匹配 frontmatter `currency: draft`，是 12 项里唯一
    「改一个单词就能翻绿」的纯词法判据；13 册正文仍写「须建设 / 0 命中」。

修复后 4 条判据均改为证据驱动，**当前 12 项判据仍为 5 SATISFIED / 7 BLOCKED、
l3Claimable=false**（本轮不翻转任何外部前置）。本测试用真仓库事实 + 内存变造
两类夹具，钉死以下不变量：

  1. 现实态：4 条判据均 BLOCKED，且归因到真实缺口（不是恒红）；
  2. 刷绿动作：改孤儿 V15 / 删 V15 / 只刷 status / 塞假 hash / 批量改
     currency / 删分册 —— 一律不得翻绿；
  3. 合规正例：注入真实合规事实后判据可达 SATISFIED（防「永远拒绝」式假实现）；
  4. fail-closed：生效真源不可读 / 分册缺失时不得判绿。
"""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATE = ROOT / "doc" / "ai-product" / "synapClaim" / "tools" / "_ai_native_readiness.py"


def _load():
    spec = importlib.util.spec_from_file_location("readiness_evidence_gate", GATE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


M = _load()


def _gate(facts, gid):
    return next(g for g in M.evaluate(facts) if g["id"] == gid)


class EvidenceDrivenGateTest(unittest.TestCase):
    def setUp(self):
        self.f = M.load_facts()

    # ---- 1. 现实态：必须 BLOCKED 且归因精确 ----
    def test_current_reality_all_four_blocked(self):
        expected_reasons = {
            "CATALOG_ADMISSION_ACTIVE": "CATALOG_POLICY_DEFAULT_NONE",
            "BASELINE_WIRED": "BASELINE_DECLARED_NOT_WIRED",
            "FROZEN_EVALSET_ACTIVE": "FROZEN_SET_NOT_ACTIVE",
            "GOLIVE_NOT_DRAFT": "GOLIVE_DRAFT_13",
        }
        for gid, reason in expected_reasons.items():
            with self.subTest(gid=gid):
                g = _gate(self.f, gid)
                self.assertEqual(g["status"], M.BLOCKED)
                self.assertEqual(g["reason"], reason)

    def test_current_reality_does_not_claim_l3(self):
        gates = M.evaluate(self.f)
        self.assertFalse(all(g["status"] == M.SATISFIED for g in gates))
        self.assertEqual(sum(g["status"] == M.BLOCKED for g in gates), 7)
        self.assertEqual(len(gates), 12)

    # ---- 2. 生效通道真源：不得被孤儿文件带偏 ----
    def test_effective_catalog_default_reads_live_channel(self):
        self.assertTrue(self.f["catalog_source_available"])
        self.assertEqual(self.f["catalog_effective_default"], "NONE")
        self.assertFalse(self.f["catalog_port_wired"])

    def test_orphan_v15_edit_cannot_flip(self):
        forged = dict(self.f, v15=self.f["v15"].replace(
            "DEFAULT 'NONE'", "DEFAULT 'MANDATORY'"))
        self.assertEqual(_gate(forged, "CATALOG_ADMISSION_ACTIVE")["status"], M.BLOCKED)

    def test_deleting_orphan_v15_cannot_flip(self):
        """原判据的致命漏洞：文件不存在 → read_text 返回 '' → 判绿。"""
        self.assertEqual(_gate(dict(self.f, v15=""), "CATALOG_ADMISSION_ACTIVE")["status"],
                         M.BLOCKED)

    def test_mandatory_without_port_stays_blocked(self):
        """配了不生效：默认值改了但无 ItemCatalogPort 实现 = 全量退补件。"""
        g = _gate(dict(self.f, catalog_effective_default="MANDATORY",
                       catalog_port_wired=False), "CATALOG_ADMISSION_ACTIVE")
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "CATALOG_PORT_NOT_WIRED")

    def test_catalog_source_unreadable_fails_closed(self):
        g = _gate(dict(self.f, catalog_source_available=False),
                  "CATALOG_ADMISSION_ACTIVE")
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "CATALOG_SOURCE_UNREADABLE")

    def test_catalog_compliant_is_reachable(self):
        g = _gate(dict(self.f, catalog_source_available=True,
                       catalog_effective_default="MANDATORY",
                       catalog_port_wired=True), "CATALOG_ADMISSION_ACTIVE")
        self.assertEqual(g["status"], M.SATISFIED)
        self.assertEqual(g["reason"], "ADMITTED")

    # ---- 3. 非 ML 基线：status 之外必须带 provenance ----
    def test_baseline_status_only_forge_cannot_flip(self):
        g = _gate(dict(self.f, baseline_all_wired=True), "BASELINE_WIRED")
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "BASELINE_SKELETON_CANNOT_CLAIM_WIRED")

    def test_baseline_without_provenance_cannot_flip(self):
        g = _gate(dict(self.f, baseline_all_wired=True,
                       baseline_top_status="confirmed"), "BASELINE_WIRED")
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertEqual(g["reason"], "BASELINE_PROVENANCE_MISSING")

    def test_baseline_compliant_is_reachable(self):
        g = _gate(dict(self.f, baseline_all_wired=True,
                       baseline_evidence_ok=True,
                       baseline_top_status="confirmed"), "BASELINE_WIRED")
        self.assertEqual(g["status"], M.SATISFIED)

    def test_baseline_facts_have_no_provenance_yet(self):
        """真实文件当前确实没有 provenance —— 钉住「尚未采集」这一事实。"""
        self.assertFalse(self.f["baseline_evidence_ok"])
        self.assertEqual(self.f["baseline_top_status"], "skeleton")

    # ---- 4. 冻结集：任意非空 hash 不得冒充 ----
    def test_fake_payload_hash_cannot_flip(self):
        forged = dict(self.f, evalset=dict(self.f["evalset"],
                                           status="active", payloadHash="aaa"))
        self.assertEqual(_gate(forged, "FROZEN_EVALSET_ACTIVE")["status"], M.BLOCKED)

    def test_hex64_without_engine_verification_cannot_flip(self):
        forged = dict(self.f, evalset=dict(self.f["evalset"],
                                           payloadHash="0" * 64))
        self.assertEqual(_gate(forged, "FROZEN_EVALSET_ACTIVE")["status"], M.BLOCKED)

    def test_frozen_compliant_is_reachable(self):
        g = _gate(dict(self.f, frozen_verified=True, frozen_status="active"),
                  "FROZEN_EVALSET_ACTIVE")
        self.assertEqual(g["status"], M.SATISFIED)

    def test_frozen_engine_contract_is_real(self):
        """判据依赖的引擎必须真实存在且 verify_manifest 可用（防桩化）。"""
        self.assertTrue(M.PATHS["frozen_engine"].exists())
        spec = importlib.util.spec_from_file_location("_fs_probe", M.PATHS["frozen_engine"])
        engine = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(engine)
        self.assertTrue(callable(engine.verify_manifest))
        self.assertTrue(callable(engine.freeze_hash))
        self.assertFalse(self.f["frozen_verified"])

    # ---- 5. Go-Live：批量改 currency 不得冒充实质完成 ----
    def test_currency_mass_edit_cannot_flip(self):
        forged = dict(self.f, golive=[dict(g, currency="current")
                                      for g in self.f["golive"]])
        self.assertEqual(_gate(forged, "GOLIVE_NOT_DRAFT")["status"], M.BLOCKED)

    def test_role_draft_cannot_flip(self):
        forged = dict(self.f, golive=[dict(g, currency="current", unfinished=False)
                                      for g in self.f["golive"]])
        self.assertEqual(_gate(forged, "GOLIVE_NOT_DRAFT")["status"], M.BLOCKED)

    def test_missing_booklet_fails_closed(self):
        # 判据已从「硬编码册数下限」改为「总纲登记集合 ↔ 磁盘集合」双向比对，
        # 归因码随之由 GOLIVE_BOOKLETS_MISSING_n（复数、数量式）改为
        # GOLIVE_BOOKLET_MISSING_n（单数、集合差式）。本用例守住 fail-closed 形态，
        # 只断言前缀而不写死 n：册数是文档侧事实，写死会让合法合并又把测试判红。
        g = _gate(dict(self.f, golive=self.f["golive"][:-2]), "GOLIVE_NOT_DRAFT")
        self.assertEqual(g["status"], M.BLOCKED)
        self.assertRegex(g["reason"], r"^GOLIVE_BOOKLET_MISSING_\d+$")

    def test_golive_compliant_is_reachable(self):
        forged = dict(self.f, golive=[dict(g, currency="current", role="current",
                                           unfinished=False) for g in self.f["golive"]])
        self.assertEqual(_gate(forged, "GOLIVE_NOT_DRAFT")["status"], M.SATISFIED)

    def test_golive_booklets_counted_from_real_files(self):
        booklets = [g for g in self.f["golive"]
                    if g["name"][:2].isdigit() and g["name"][:2] != "00"]
        self.assertEqual(len(booklets), 13)
        self.assertTrue(all(g["unfinished"] or g["currency"].startswith("draft")
                            for g in booklets))


if __name__ == "__main__":
    unittest.main(verbosity=2)
