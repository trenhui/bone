#!/usr/bin/env python3
"""组织覆盖审计：回答「哪些**真实发起人**会因为组织数据缺失，让单据在审批节点挂起」。

为什么不能只看「有多少账号配了主管」
--------------------------------------
覆盖率用「账号总数」做分母会给出一个吓人但无用的数字。实测 tenant 0：21 个账号里
只有 2 个配了主管，看起来 9.5% —— 但另外 19 个是 E2E 残留（E2E056575、c3view※、c982※※），
它们从来不发起单据。真正在用审批流的是 `admin` 一个账号，而它的主管就在位。
反过来，一个「覆盖率 95%」的租户完全可能恰好把 5% 落在全部收单员身上。

所以本脚本的分母取自**真实流量**：`wf_instance.started_by` 里出现过的账号。
这是「谁真的发起过单据」，不是「谁存在于通讯录」。业界的等价做法是用实际请求量定义
SLO 的分母，而不是用注册用户数 —— 两者差别就在这里。

判据必须和引擎一致，否则报表会给出假的安全感
----------------------------------------------
逐条复刻 `RecordFieldApproverResolver` + `WorkflowEngineImpl` 的裁决：

  · 策略取自**已发布且启用**的定义中**最新版本**（引擎走 findLatestPublished，
    不是「库里最大版本号」——一个更大的版本若 enabled=0 则不参与路由）。
  · LINE_MANAGER / DEPT_HEAD / LEVEL_UP 解析为空 → 引擎挂起（失败关闭）。
  · 解析出的人**等于发起人本人** → 引擎同样挂起（自审批，违反职责分离）。
    这一类最容易被漏报：数据全配了，单据照样卡死。

用法
----
  python3 scripts/audit-org-coverage.py                 # 全租户风险报表
  python3 scripts/audit-org-coverage.py --tenant 1001   # 只看某租户
  python3 scripts/audit-org-coverage.py --verbose       # 同时列出无风险发起人
  python3 scripts/audit-org-coverage.py --strict        # 有风险则退出码 1（可挂 CI / 上线前置检查）

本脚本**只读**，不写任何表。环境变量同 seed-bootstrap.py：
BONE_DB_HOST / BONE_DB_PORT / BONE_DB_USER / BONE_DB_PASSWORD / BONE_DB_NAME。
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

# 与引擎 WorkflowDefinitionValidator.SUPPORTED_STRATEGIES 对齐的「需要组织数据」子集。
ORG_STRATEGIES = {"LINE_MANAGER", "DEPT_HEAD", "LEVEL_UP"}


def find_client(name: str) -> str | None:
    """定位 mysql 客户端。与 seed-bootstrap.py 保持同一套回退顺序 —— 本机默认不在 PATH 上。"""
    explicit = os.environ.get(f"{name.upper()}_CLIENT")
    if explicit and Path(explicit).exists():
        return explicit
    found = shutil.which(name)
    if found:
        return found
    for cand in (f"/usr/local/mysql/bin/{name}", f"/opt/homebrew/bin/{name}", f"/usr/bin/{name}"):
        if Path(cand).exists():
            return cand
    return None


@dataclass
class Db:
    name: str
    host: str
    port: str
    user: str
    password: str

    @property
    def conn(self) -> list[str]:
        return [f"-h{self.host}", f"-P{self.port}", f"-u{self.user}", f"-p{self.password}"]

    def rows(self, sql: str) -> list[dict[str, str]]:
        """执行只读查询，返回 dict 行（列名来自 -B 输出的首行表头）。"""
        cli = find_client("mysql")
        if not cli:
            raise SystemExit("❌ 找不到 mysql 客户端（设 MYSQL_CLIENT 或装到 PATH）")
        out = subprocess.run(
            [cli, *self.conn, "-B", self.name, "-e", sql],
            capture_output=True,
            text=True,
        )
        if out.returncode != 0:
            tail = (out.stderr.strip().splitlines() or ["(无输出)"])[-1]
            raise SystemExit(f"❌ 查询失败：{tail}")
        lines = out.stdout.splitlines()
        if not lines:
            return []
        header = lines[0].split("\t")
        return [dict(zip(header, ln.split("\t"))) for ln in lines[1:] if ln]


def env(key: str, default: str) -> str:
    return os.environ.get(key) or default


@dataclass
class Account:
    id: str
    username: str
    manager_id: str | None
    dept_id: str | None


@dataclass
class Risk:
    tenant: str
    account_id: str
    username: str
    def_key: str
    version: str
    node_code: str
    strategy: str
    reason: str


@dataclass
class TenantReport:
    tenant: str
    initiators: int = 0
    risky_accounts: set[str] = field(default_factory=set)
    risks: list[Risk] = field(default_factory=list)
    skipped_defs: list[str] = field(default_factory=list)


def load_accounts(db: Db, tenant: str) -> dict[str, Account]:
    rows = db.rows(
        "SELECT id, username, IFNULL(manager_id,'') AS manager_id, IFNULL(dept_id,'') AS dept_id "
        f"FROM iam_account WHERE tenant_id = {int(tenant)} AND deleted = 0 AND status = 1"
    )
    return {
        r["id"]: Account(
            id=r["id"],
            username=r["username"],
            manager_id=r["manager_id"] or None,
            dept_id=r["dept_id"] or None,
        )
        for r in rows
    }


def load_dept_leaders(db: Db, tenant: str) -> dict[str, str | None]:
    rows = db.rows(
        "SELECT id, IFNULL(leader_id,'') AS leader_id FROM iam_dept "
        f"WHERE tenant_id = {int(tenant)} AND deleted = 0"
    )
    return {r["id"]: (r["leader_id"] or None) for r in rows}


def load_active_definitions(db: Db, tenant: str) -> list[dict]:
    """取每个 def_key 下 **已启用** 的最高版本 —— 复刻引擎的 findLatestPublished。

    只取 `enabled=1` 是刻意的：库里有大量历史版本（v1/v2）仍 enabled，若把每个版本都算进
    风险，会把「早已下线的老配置」报成当前风险，报表立刻失去可信度。
    """
    rows = db.rows(
        "SELECT def_key, def_version, definition_json FROM wf_process_def d "
        f"WHERE tenant_id = {int(tenant)} AND enabled = 1 AND deleted = 0 "
        "AND def_version = (SELECT MAX(def_version) FROM wf_process_def x "
        f"WHERE x.tenant_id = {int(tenant)} AND x.def_key = d.def_key "
        "AND x.enabled = 1 AND x.deleted = 0)"
    )
    defs = []
    for r in rows:
        try:
            body = json.loads(r["definition_json"])
        except (TypeError, ValueError):
            continue
        for node_code, rules in (body.get("assignments") or {}).items():
            for rule in rules or []:
                strategy = (rule or {}).get("strategy")
                if strategy in ORG_STRATEGIES:
                    defs.append(
                        {
                            "def_key": r["def_key"],
                            "version": r["def_version"],
                            "node_code": node_code,
                            "strategy": strategy,
                            "params": (rule or {}).get("params") or {},
                        }
                    )
    return defs


def resolve_account(
    account: Account,
    strategy: str,
    params: dict,
    accounts: dict[str, Account],
    leaders: dict[str, str | None],
) -> tuple[str | None, str]:
    """返回 (可用的审批人 | None, 说明)。None 表示引擎会挂起。

    判据必须逐条对齐 `RecordFieldApproverResolver`，包括<b>自审批上溯</b>：
    部门负责人自己发单时 DEPT_HEAD 必然解析到本人，解析器会换成其主管；只有上溯也无人才挂起。
    若这里不跟着改，报表会持续报「已修好的风险」，几次之后就没人再信它了。
    """
    resolved, why = _resolve_raw(account, strategy, params, accounts, leaders)
    if resolved is None or resolved != account.id:
        return resolved, why
    escalated = account.manager_id
    if escalated and escalated != account.id:
        return escalated, f"{why}（解析到本人，已上溯至其主管 {escalated}）"
    return None, f"{why}是发起人本人，且其本人无主管可上溯（顶层）"


def _resolve_raw(
    account: Account,
    strategy: str,
    params: dict,
    accounts: dict[str, Account],
    leaders: dict[str, str | None],
) -> tuple[str | None, str]:
    """按策略解析，不做自审批处理。"""
    if strategy == "LINE_MANAGER":
        if not account.manager_id:
            return None, "manager_id 为空（未配置直属主管）"
        return account.manager_id, "直属主管"

    if strategy == "DEPT_HEAD":
        if not account.dept_id:
            return None, "未归属任何部门（dept_id 为空）"
        leader = leaders.get(account.dept_id)
        if not leader:
            return None, f"所在部门 {account.dept_id} 未指定负责人（leader_id 为空）"
        return leader, "部门负责人"

    if strategy == "LEVEL_UP":
        levels = params.get("level", 1)
        try:
            levels = int(levels)
        except (TypeError, ValueError):
            return None, f"LEVEL_UP 的 level 参数非法：{levels!r}"
        current = account
        seen = {account.id}
        for _ in range(max(levels, 1)):
            if not current.manager_id:
                return None, f"向上第 {levels} 级的汇报链中断（{current.username} 无主管）"
            if current.manager_id in seen:
                return None, f"汇报链存在环（回到 {current.manager_id}）"
            nxt = accounts.get(current.manager_id)
            if nxt is None:
                return None, f"主管 {current.manager_id} 不在本租户有效账号中（跨租户或已停用）"
            seen.add(nxt.id)
            current = nxt
        return current.id, f"向上 {levels} 级主管"

    return None, f"未识别的组织策略 {strategy}"


def audit_tenant(
    db: Db, tenant: str, verbose: bool, only_initiators: bool = True
) -> tuple[TenantReport, dict[str, Account], list[dict]]:
    accounts = load_accounts(db, tenant)
    leaders = load_dept_leaders(db, tenant)
    defs = load_active_definitions(db, tenant)
    report = TenantReport(tenant=tenant)

    if not defs:
        # 该租户没有启用中的组织策略定义 ⇒ 不存在「按组织解析」的挂起风险。
        report.skipped_defs.append("无启用中的定义使用组织策略（LINE_MANAGER/DEPT_HEAD/LEVEL_UP）")
        return report, accounts, defs

    initiator_rows = db.rows(
        "SELECT DISTINCT started_by FROM wf_instance "
        f"WHERE tenant_id = {int(tenant)} AND deleted = 0 AND IFNULL(started_by,'') <> ''"
    )
    initiator_ids = {r["started_by"] for r in initiator_rows}

    # 审计对象 = 真实发起人 ∩ 当前有效账号。
    # 若某人发起过后就被停用/删除，他不会再发起，算进风险只会制造噪声。
    targets = (
        [accounts[i] for i in sorted(initiator_ids & set(accounts))]
        if only_initiators
        else sorted(accounts.values(), key=lambda a: a.id)
    )
    report.initiators = len(targets)

    for account in targets:
        for node in defs:
            resolved, why = resolve_account(
                account, node["strategy"], node["params"], accounts, leaders
            )
            if resolved is None:
                # 解析器已经处理过「解析到本人则上溯」；能走到这里的只有
                # 真正无人可派的情况（无主管 / 无负责人 / 上溯到顶仍是本人）。
                report.risky_accounts.add(account.id)
                report.risks.append(
                    Risk(
                        tenant=tenant,
                        account_id=account.id,
                        username=account.username,
                        def_key=node["def_key"],
                        version=node["version"],
                        node_code=node["node_code"],
                        strategy=node["strategy"],
                        reason=why,
                    )
                )
            elif verbose:
                print(
                    f"    ok  {account.username:<24} {node['def_key']}/{node['node_code']}"
                    f"  {node['strategy']:<12} → {resolved}（{why}）"
                )
    return report, accounts, defs


def main() -> int:
    parser = argparse.ArgumentParser(
        description="审计组织数据覆盖：哪些真实发起人会因组织数据缺失导致审批挂起"
    )
    parser.add_argument("--tenant", type=int, help="只审计指定租户（缺省：库中全部租户）")
    parser.add_argument("--verbose", action="store_true", help="同时列出无风险的发起人")
    parser.add_argument(
        "--strict", action="store_true", help="存在风险时退出码为 1（用于上线前置检查 / CI）"
    )
    parser.add_argument(
        "--all-accounts",
        action="store_true",
        help="把全部有效账号当作审计对象（缺省只用真实发起人，避免分母虚高）",
    )
    args = parser.parse_args()

    db = Db(
        name=env("BONE_DB_NAME", "bone"),
        host=env("BONE_DB_HOST", "127.0.0.1"),
        port=env("BONE_DB_PORT", "3306"),
        user=env("BONE_DB_USER", "root"),
        password=env("BONE_DB_PASSWORD", "mysql123"),
    )

    if args.tenant is not None:
        tenants = [str(args.tenant)]
    else:
        tenants = [
            r["tenant_id"]
            for r in db.rows("SELECT DISTINCT tenant_id FROM iam_account WHERE deleted = 0 ORDER BY tenant_id")
        ]

    total_risks = 0
    risky_accounts_all: set[str] = set()
    print("=" * 96)
    print("组织覆盖审计（判据与引擎一致：解析不出人 ⇒ 挂起；解析出本人 ⇒ 自审批挂起）")
    print("分母 = 真实发起过单据的账号（wf_instance.started_by），不是账号总数")
    print("=" * 96)

    for tenant in tenants:
        print(f"\n--- 租户 {tenant} ---")
        report, accounts, defs = audit_tenant(
            db, tenant, args.verbose, only_initiators=not args.all_accounts
        )
        if not defs:
            print(f"  跳过：{report.skipped_defs[0]}")
            continue
        strategies = sorted({f"{d['def_key']}/{d['node_code']}:{d['strategy']}" for d in defs})
        print(f"  在用的组织策略节点：{', '.join(strategies)}")
        print(f"  审计对象（真实发起人）：{report.initiators} 人")
        if not report.risks:
            print("  ✅ 无挂单风险：所有真实发起人在每个组织策略节点上都能解析到非本人的审批人")
            continue
        risky_accounts_all |= report.risky_accounts
        total_risks += len(report.risks)
        print(
            f"  ⚠️  {len(report.risky_accounts)}/{report.initiators} 人会挂单"
            f"（共 {len(report.risks)} 处节点级风险）"
        )
        print()
        print(f"    {'账号':<20} {'用户名':<22} {'流程/节点':<34} {'策略':<12} 原因")
        print(f"    {'-' * 20} {'-' * 22} {'-' * 34} {'-' * 12} {'-' * 40}")
        for risk in report.risks:
            print(
                f"    {risk.account_id:<20} {risk.username[:22]:<22} "
                f"{(risk.def_key + '/' + risk.node_code)[:34]:<34} "
                f"{risk.strategy:<12} {risk.reason}"
            )

    print("\n" + "=" * 96)
    if total_risks:
        print(f"结论：{len(risky_accounts_all)} 个真实发起人在 {total_risks} 处节点会挂单（fail-closed，不会放行）。")
        print("修复路径：补 iam_account.manager_id / iam_dept.leader_id，或用 assignTask 直接指派。")
        return 1 if args.strict else 0
    print("结论：无挂单风险。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
