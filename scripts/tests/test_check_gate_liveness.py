#!/usr/bin/env python3
"""check-gate-liveness.py 的 R4（孤儿门禁检测）自测。

判据背景见 scripts/check-gate-liveness.py 的模块 docstring：R1–R3 只能看见
「已列出的步骤」，看不见「从未被任何入口列出的脚本」。2026-11-05 实测 61 个
check-*.py 里有 3 个判据与单测齐备却全仓零调用 —— 目录里躺着脚本会让读者以为
该维度已被覆盖，所以需要 R4 把「写好了但没人跑」变成会红的缺陷。

本测试用最小夹具（临时目录 + 手写入口文件）覆盖 R4/R4b/R4c/R4d 的每个分支，
并特别覆盖两类「不该红」的形态：注释提及、前缀同名 —— 误报会让门禁被整体关闭。
"""

import importlib.util
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-gate-liveness.py",
)
_spec = importlib.util.spec_from_file_location("check_gate_liveness", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _write(root, rel, body):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(body)


class OrphanGateTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="gate-liveness-")
        # 默认给一个存在但"什么门禁都没调"的入口：真实仓库里入口必然存在，
        # 缺入口是 R4d 的独立用例（显式删除），不应污染其他分支的夹具。
        self._check_sh("echo no-gate\n")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _gate(self, name="check-orphan.py"):
        _write(self.root, "scripts/" + name, "#!/usr/bin/env python3\n")

    def _check_sh(self, body):
        _write(self.root, "scripts/check.sh", body)

    def _ci(self, body):
        _write(self.root, ".github/workflows/ci.yml", body)

    def _allowlist(self, entries, raw=None):
        doc = {"unwired": entries} if raw is None else raw
        _write(self.root, "scripts/gate-liveness-allowlist.json", json.dumps(doc))

    # ---- 正常路径 -------------------------------------------------------
    def test_wired_gate_is_not_reported(self):
        self._gate()
        self._check_sh("python3 scripts/check-orphan.py\n")

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(
            stats,
            {"gates": 1, "wired": 1, "exempt": 0, "ci_only": 0, "unaccounted_unwired": []},
        )

    def test_workflow_counts_as_entry(self):
        self._gate()
        self._ci("      - run: /usr/bin/env python3 scripts/check-orphan.py --check\n")

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["wired"], 1)

    # ---- 孤儿 -----------------------------------------------------------
    def test_orphan_gate_is_reported(self):
        self._gate()

        violations, stats = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4:") for v in violations), violations)
        self.assertEqual(stats["wired"], 0)

    def test_comment_mention_does_not_count_as_wiring(self):
        # 「见 scripts/check-orphan.py 同款口径」是注释，不会执行 ⇒ 仍应判孤儿
        self._gate()
        self._check_sh("# 判据同 scripts/check-orphan.py，此处不调用\n")

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4:") for v in violations), violations)

    def test_prefix_same_name_is_not_wiring(self):
        # 同名前缀另一个脚本不能把本脚本"接"上线
        self._gate()
        _write(self.root, "scripts/check-orphan-extra.py", "#!/usr/bin/env python3\n")
        self._check_sh("python3 scripts/check-orphan-extra.py\n")

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(
            any("check-orphan.py" in v and v.startswith("R4:") for v in violations),
            violations,
        )

    def test_tests_dir_scripts_are_not_gates(self):
        # scripts/tests/ 下是门禁自测夹具，不是门禁本体
        _write(self.root, "scripts/tests/check-fixture.py", "#!/usr/bin/env python3\n")
        self._check_sh("echo ok\n")

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["gates"], 0)

    # ---- 豁免 -----------------------------------------------------------
    def test_exempted_orphan_passes(self):
        self._gate()
        self._allowlist(
            [
                {
                    "script": "scripts/check-orphan.py",
                    "reason": "脚本头部要求接线前先走架构师评审，否则等于绕过审批扩大提交门禁",
                    "owner": "架构师评审",
                }
            ]
        )

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["exempt"], 1)

    def test_short_reason_is_reported(self):
        self._gate()
        self._allowlist(
            [{"script": "scripts/check-orphan.py", "reason": "待评估", "owner": "someone"}]
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4c:") for v in violations), violations)

    def test_missing_owner_is_reported(self):
        self._gate()
        self._allowlist(
            [
                {
                    "script": "scripts/check-orphan.py",
                    "reason": "脚本头部要求接线前先走架构师评审，否则等于绕过审批扩大提交门禁",
                }
            ]
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any("缺 owner" in v for v in violations), violations)

    def test_allowlist_entry_for_wired_gate_is_reported(self):
        # 豁免只可收缩：已经接线了还挂着豁免 ⇒ 判红
        self._gate()
        self._check_sh("python3 scripts/check-orphan.py\n")
        self._allowlist(
            [
                {
                    "script": "scripts/check-orphan.py",
                    "reason": "脚本头部要求接线前先走架构师评审，否则等于绕过审批扩大提交门禁",
                    "owner": "架构师评审",
                }
            ]
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4b:") for v in violations), violations)

    def test_allowlist_entry_for_missing_script_is_reported(self):
        self._gate()
        self._allowlist(
            [
                {
                    "script": "scripts/check-deleted.py",
                    "reason": "脚本头部要求接线前先走架构师评审，否则等于绕过审批扩大提交门禁",
                    "owner": "架构师评审",
                }
            ]
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any("check-deleted.py" in v for v in violations), violations)

    def test_malformed_allowlist_is_reported(self):
        self._gate()
        self._allowlist(None, raw={"unwired": "not-a-list"})

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4c:") for v in violations), violations)

    # ---- 防假绿 ---------------------------------------------------------
    def test_no_entry_file_fails_loudly(self):
        # 入口路径失效时不能「因为没入口所以跳过」——那会让 R4 恒绿
        self._gate()
        os.remove(os.path.join(self.root, "scripts", "check.sh"))

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4d:") for v in violations), violations)

    # ---- R4e：派生入口必须被根入口调用 ---------------------------------
    def test_derived_entry_without_caller_is_reported(self):
        # 一个没人调的 runner 不能给门禁发免死金牌：门禁「接线」到它，但它是死的
        self._gate()
        _write(self.root, "scripts/ci-check.sh", "python3 scripts/check-orphan.py\n")

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4e:") for v in violations), violations)

    def test_derived_entry_called_by_manual_runner_passes(self):
        self._gate()
        _write(self.root, "scripts/ci-check.sh", "python3 scripts/check-orphan.py\n")
        _write(self.root, "scripts/ci-local.sh", "bash scripts/ci-check.sh\n")

        violations, _ = mod.orphan_violations(self.root)

        self.assertFalse(any(v.startswith("R4e:") for v in violations), violations)

    def test_derived_entry_called_by_workflow_passes(self):
        self._gate()
        _write(self.root, "scripts/ci-check.sh", "python3 scripts/check-orphan.py\n")
        self._ci("      - run: bash scripts/ci-check.sh\n")

        violations, _ = mod.orphan_violations(self.root)

        self.assertFalse(any(v.startswith("R4e:") for v in violations), violations)

    def test_gate_wired_only_outside_precommit_without_registration_is_unaccounted(self):
        # 接在人工入口也是真接线（orphan_violations 不判红），但因未在 allowlist 登记
        # 刻意留 CI-only，落入 unaccounted_unwired —— 由 R6 在 --strict 下判红。
        self._gate()
        _write(self.root, "scripts/ci-local.sh", "python3 scripts/check-orphan.py\n")

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["unaccounted_unwired"], ["scripts/check-orphan.py"])
        self.assertEqual(stats["ci_only"], 0)

    def test_gate_wired_in_precommit_is_not_unaccounted(self):
        self._gate()
        self._check_sh("python3 scripts/check-orphan.py\n")

        _, stats = mod.orphan_violations(self.root)

        self.assertEqual(stats["unaccounted_unwired"], [])

    # ---- R6：partial_wire 豁免 -------------------------------------------
    def test_partial_wire_ci_only_counts_and_passes(self):
        # 已接人工/CI 入口但未进 pre-commit，且 allowlist 登记 kind=ci_only ⇒ 算 CI-only，不判红。
        self._gate()
        _write(self.root, "scripts/ci-local.sh", "python3 scripts/check-orphan.py\n")
        self._allowlist(
            None,
            raw={
                "unwired": [],
                "partial_wire": [
                    {
                        "script": "scripts/check-orphan.py",
                        "kind": "ci_only",
                        "reason": "L3 口径门禁须架构师在 gate-state.json 登记真实载体后方可阻断，且全量扫描约 22s，进 pre-commit 会拖慢每次提交。",
                        "owner": "架构师评审",
                    }
                ],
            },
        )

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["ci_only"], 1)
        self.assertEqual(stats["unaccounted_unwired"], [])

    def test_partial_wire_known_incomplete_counts_and_passes(self):
        self._gate()
        _write(self.root, "scripts/ci-local.sh", "python3 scripts/check-orphan.py\n")
        self._allowlist(
            None,
            raw={
                "unwired": [],
                "partial_wire": [
                    {
                        "script": "scripts/check-orphan.py",
                        "kind": "known_incomplete",
                        "reason": "存量缺口登记在 baseline.json（只可收缩），尚有 12 张表待补声明；直接进 pre-commit 会因存量缺口误拦正常提交。",
                        "owner": "架构师评审",
                    }
                ],
            },
        )

        violations, stats = mod.orphan_violations(self.root)

        self.assertEqual(violations, [])
        self.assertEqual(stats["ci_only"], 1)

    def test_partial_wire_entry_for_precommit_gate_is_r4b(self):
        # 豁免只可收缩：门禁已进 pre-commit 还挂着 partial_wire 条目 ⇒ R4b 判红。
        self._gate()
        self._check_sh("python3 scripts/check-orphan.py\n")
        self._allowlist(
            None,
            raw={
                "unwired": [],
                "partial_wire": [
                    {
                        "script": "scripts/check-orphan.py",
                        "kind": "ci_only",
                        "reason": "L3 口径门禁须架构师评审后方可阻断，且扫描较慢。",
                        "owner": "架构师评审",
                    }
                ],
            },
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(any(v.startswith("R4b:") for v in violations), violations)

    def test_partial_wire_missing_kind_is_reported(self):
        self._gate()
        _write(self.root, "scripts/ci-local.sh", "python3 scripts/check-orphan.py\n")
        self._allowlist(
            None,
            raw={
                "unwired": [],
                "partial_wire": [
                    {
                        "script": "scripts/check-orphan.py",
                        "reason": "缺少 kind 字段。",
                        "owner": "架构师评审",
                    }
                ],
            },
        )

        violations, _ = mod.orphan_violations(self.root)

        self.assertTrue(
            any("kind 必须是" in v for v in violations), violations
        )

    def test_r6_notes_fires_for_unaccounted(self):
        stats = {
            "gates": 1,
            "wired": 0,
            "exempt": 0,
            "ci_only": 0,
            "unaccounted_unwired": ["scripts/check-orphan.py"],
        }

        notes = mod.r6_notes(stats)

        self.assertEqual(len(notes), 1)
        self.assertIn("R6:", notes[0])

    def test_r6_notes_empty_when_all_registered(self):
        stats = {
            "gates": 1,
            "wired": 1,
            "exempt": 0,
            "ci_only": 1,
            "unaccounted_unwired": [],
        }

        self.assertEqual(mod.r6_notes(stats), [])

    def test_r6_strict_integration_exit_codes(self):
        # 在进程内跑 main()（monkeypatch REPO/CI_YML 指向夹具），证明 --strict 下
        # 漏接门禁 exit 1、登记后 exit 0。ci.yml 给 15 条 (blocking) 步骤以满足 R2。
        self._gate()
        self._check_sh("echo precommit-only\n")  # pre-commit 入口不调该门禁
        _write(self.root, "scripts/ci-local.sh", "python3 scripts/check-orphan.py\n")

        def ci_with_blocking(n):
            head = (
                "on:\n"
                "  push:\n"
                "    branches: [dev]\n"
                "  pull_request:\n"
                "    branches: [dev]\n"
                "jobs:\n"
                "  backend-quality:\n"
                "    runs-on: ubuntu-latest\n"
                "    steps:\n"
            )
            steps = "".join(
                f"      - name: gate {i} (blocking)\n        run: echo ok\n" for i in range(n)
            )
            return head + steps

        self._ci(ci_with_blocking(15))

        saved_repo, saved_ci = mod.REPO, mod.CI_YML
        saved_argv = sys.argv
        mod.REPO = Path(self.root)
        mod.CI_YML = Path(self.root) / ".github" / "workflows" / "ci.yml"
        try:
            # 未登记 ⇒ 漏接 ⇒ --strict 判红 ⇒ exit 1
            self._allowlist(None, raw={"unwired": [], "partial_wire": []})
            sys.argv = ["check-gate-liveness.py", "--strict"]
            code_unregistered = mod.main()

            # 登记 kind=ci_only ⇒ exit 0
            self._allowlist(
                None,
                raw={
                    "unwired": [],
                    "partial_wire": [
                        {
                            "script": "scripts/check-orphan.py",
                            "kind": "ci_only",
                            "reason": "L3 口径门禁须架构师评审后方可阻断，且全量扫描约 22s。",
                            "owner": "架构师评审",
                        }
                    ],
                },
            )
            code_registered = mod.main()
        finally:
            mod.REPO, mod.CI_YML = saved_repo, saved_ci
            sys.argv = saved_argv

        self.assertEqual(code_unregistered, 1)
        self.assertEqual(code_registered, 0)


class TriggerCoverageTest(unittest.TestCase):
    """R1：push.branches 过滤源分支、pull_request.branches 过滤目标分支，不能混用。"""

    TRIGGERS = {"push": ["dev", "main", "master"], "pull_request": ["dev", "main", "master"]}

    def test_protected_branch_in_both_lists_passes(self):
        violations, _ = mod.trigger_violations("dev", "", self.TRIGGERS)

        self.assertEqual(violations, [])

    def test_no_trigger_parsed_is_violation(self):
        violations, _ = mod.trigger_violations("dev", "", {})

        self.assertTrue(any(v.startswith("R1:") for v in violations), violations)

    def test_unknown_branch_everywhere_is_note_not_violation(self):
        # 本地特性分支：push 不会触发是**预期行为**，覆盖由 PR 承担 ⇒ 不能判红
        violations, notes = mod.trigger_violations("feat/ai-native", "", self.TRIGGERS)

        self.assertEqual(violations, [])
        self.assertTrue(notes)

    def test_pr_from_feature_branch_into_dev_passes(self):
        # PR 的 branches 过滤目标分支：源分支是特性分支不影响 CI 是否触发
        violations, _ = mod.trigger_violations("feat/x", "dev", self.TRIGGERS)

        self.assertEqual(violations, [])

    def test_pr_into_uncovered_base_is_violation(self):
        violations, _ = mod.trigger_violations("feat/x", "release", self.TRIGGERS)

        self.assertTrue(any("目标分支" in v for v in violations), violations)

    def test_protected_branch_dropped_from_push_is_violation(self):
        # P0-1 原形态：dev 从 push 触发器里被删掉
        triggers = {"push": ["main", "master"], "pull_request": ["dev", "main", "master"]}

        violations, _ = mod.trigger_violations("dev", "", triggers)

        self.assertTrue(any(v.startswith("R1:") for v in violations), violations)

    def test_undeterminable_branch_is_note_only(self):
        violations, notes = mod.trigger_violations("", "", self.TRIGGERS)

        self.assertEqual(violations, [])
        self.assertTrue(notes)


if __name__ == "__main__":
    unittest.main(verbosity=2)
