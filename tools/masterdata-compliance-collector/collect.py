#!/usr/bin/env python3
"""主数据模块 Docs-as-Code 合规收集器。"""
from __future__ import annotations

import argparse
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import compliance_lib as cl  # noqa: E402

COLLECTOR_DIR = Path(__file__).resolve().parent
OUT_DIR = ROOT / "doc" / "_generated" / "masterdata"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "masterdata-v1.yaml"
MAIN = ROOT / "bone-platform" / "bone-masterdata" / "src" / "main" / "java"
TEST = ROOT / "bone-platform" / "bone-masterdata" / "src" / "test" / "java"
INIT_SQL = ROOT / "bone-init.sql"
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "3. 主数据管理模块详细设计方案.md"
GOVERNANCE_DOC = (
    ROOT / "doc" / "design" / "modules"
    / "3a. 主数据管理模块核心场景及用例设计方案.md"
)
# Steward RACI / SoD 是 MDM 头号失败模式的防线；锚点写在脚本里而非被校验文档中，
# 避免「期望值来自被校验对象」的闭环自证。删掉整段表格会让 --check 转红。
RACI_ANCHORS = ("Steward RACI", "SoD 矩阵", "具名 Data Owner")
AS_IS_MARKER = ("<!-- MDM_COMPLIANCE_ASIS:START -->", "<!-- MDM_COMPLIANCE_ASIS:END -->")
BACKLOG_MARKER = ("<!-- MDM_COMPLIANCE_BACKLOG:START -->", "<!-- MDM_COMPLIANCE_BACKLOG:END -->")

# 能力探针：以源码为唯一真源判定 [Target]/[Vision] 项是否仍未实现。
# 过去 backlog.yaml 的 tracking 是人工文本，曾出现「status 未落库」但源码已实现
# 六态审批 + SoD + 版本快照的漂移（2026-11-13 复核）。此后由 CI 守护：
# probe 有源码命中却仍列在 backlog => 文档称缺而代码已有，判红；
# probe 无命中但 backlog 缺项 => 代码未实现却无跟踪，判红。
#
# 2026-11-13 二次校准（编排联调落地后）：
#   (1) 旧 golden-record-merge 的 scope=domain、completion 却锚定 ApplicationService/Controller，
#       是永远不可能命中的死判据（假阴）；改为 scope=all 并拆出 orchestration 判据。
#   (2) 「编排/API 接通」不等于「能力完整交付」。implemented 现在要求 ddl 真源中对应建表语句
#       全部存在（生产持久化的客观下界），只用源码正则会因 Javadoc 里提到表名而假绿（已实测）。
#   (3) 无 ddl/orchestration 配置的老能力保持原三态语义，行为逐字不变。
CAPABILITY_PROBES = {
    "catalog-sync-adr-full": {
        "presence": r"sourceEntityId|InstantiateFromTemplate|convert",
        "completion": r"CatalogSyncService|MD_CATALOG_SYNC|metaEntitySync",
        "scope": "application",
    },
    "quality-rule-engine": {
        "presence": r"PerformDataQualityCheck|QualityApplicationService",
        "completion": r"BatchQualityCheckJob|QualityRemediationWorkOrder",
        "scope": "application",
    },
    "publish-workflow": {
        "presence": r"SOD_VIOLATION|WORKFLOW_APPROVAL_REQUIRED|submitForApproval",
        "completion": r"SOD_VIOLATION",
        "scope": "application",
    },
    "golden-record-merge": {
        "presence": r"GoldenRecord|Survivorship|MergeAudit|GoldenRecordMerger",
        "completion": r"GoldenRecordMergeApplicationService",
        "orchestration": r"GoldenRecordApplicationService|GoldenRecordController|GoldenRecordPort",
        "ddl": ["mdm_golden_record", "mdm_merge_audit"],
        "scope": "all",
    },
    "entity-resolution-xref": {
        "presence": r"KeyMapping|Xref|CrossReference|EntityKeyMapping",
        "completion": r"KeyMappingApplicationService|XrefController",
        "orchestration": r"EntityKeyMappingPort|EntityKeyMappingRegistry",
        "ddl": ["mdm_xref"],
        "scope": "all",
    },
    "masterdata-outbox": {
        "presence": r"OutboxRelay|mdm_outbox|RecordDispatchedEvent|MasterDataRecordDispatchedEvent",
        "completion": r"MasterDataOutboxRelay|RecordDispatchApplicationService",
        "orchestration": r"MasterDataOutboxPort|MasterDataRecordDispatchedEvent",
        "relay": r"OutboxRelayStorePort|MasterDataProjectionPort|deadLetters\(|reconcile\(",
        "ddl": ["mdm_outbox"],
        "scope": "all",
    },
    "field-level-lineage": {
        "presence": r"FieldLineage|FieldProvenance|sourceRecordId.*ruleId",
        "completion": r"FieldLineageRepository|FieldLineageApplicationService",
        "orchestration": r"FieldProvenance|GoldenRecordApplicationService",
        "ddl": ["mdm_field_lineage"],
        "scope": "all",
    },
    "change-request-segmented": {
        "presence": r"ChangeRequest|SegmentApproval",
        "completion": r"ChangeRequestApplicationService|SegmentApprovalController",
        "orchestration": r"ChangeRequestApplicationService|ChangeRequestWorkflowPort|SegmentApproval",
        "ddl": ["mdm_change_request", "mdm_change_request_segment"],
        "scope": "all",
    },
}


def ddl_tables_present(tables: list[str]) -> list[str]:
    """DDL 真源中真实存在的 CREATE TABLE 名称；注释/文档里的表名不算交付证据。"""
    sql = cl.read(INIT_SQL)
    found = []
    for table in tables:
        pattern = r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?" + re.escape(table) + r"\b"
        if re.search(pattern, sql, re.IGNORECASE):
            found.append(table)
    return found


def scan_capabilities() -> dict:
    """扫描源码 + DDL 判定三态。

    implemented = 编排/API 已接通 **且** ddl 真源中对应建表语句齐全；
    partial     = 有领域内核或编排痕迹，但生产持久化未交付；
    missing     = 无任何痕迹。
    """
    scopes = {
        "application": MAIN / "com" / "bone" / "masterdata" / "application",
        "domain": MAIN / "com" / "bone" / "masterdata" / "domain",
        "all": MAIN,
    }
    result = {}
    for cap, cfg in CAPABILITY_PROBES.items():
        scope = scopes[cfg["scope"]]
        presence = cl.grep_files(scope, cfg["presence"], ROOT)
        completion = cl.grep_files(scope, cfg["completion"], ROOT)
        # 老能力没有显式编排判据时 orchestration 记 0（不要拿 completion 兜底，
        # 否则附录里 orchestrated 会把已完成的老能力也算进「编排已接通」）。
        orchestration = (
            cl.grep_files(scope, cfg["orchestration"], ROOT)
            if cfg.get("orchestration")
            else []
        )
        relay = cl.grep_files(scope, cfg["relay"], ROOT) if cfg.get("relay") else []
        if cfg.get("ddl") is not None:
            ddl_found = ddl_tables_present(cfg["ddl"])
            delivered = len(ddl_found) == len(cfg["ddl"])
            delivery_hits = ddl_found
        else:
            delivered = bool(completion)
            delivery_hits = [h.rsplit("/", 1)[-1] for h in completion]
        wired = bool(orchestration) or bool(completion)
        status = (
            "implemented"
            if delivered and wired
            else ("partial" if (presence or orchestration) else "missing")
        )
        result[cap] = {
            "status": status,
            "presenceHits": len(presence),
            "completionHits": len(completion),
            "orchestrationHits": len(orchestration),
            "relayHits": len(relay),
            "deliveryHits": len(delivery_hits),
            "ddlMissing": [t for t in cfg.get("ddl", []) if t not in delivery_hits],
            "orchestrated": bool(orchestration),
            "delivered": delivered,
            "hitsSample": [h.rsplit("/", 1)[-1] for h in presence[:3]],
        }
    return result


def assert_backlog_consistency(backlog: list[dict], probes: dict) -> list[str]:
    """backlog 声明必须与源码探针一致，否则 CI 判红（防文档漂移 / 假红）。"""
    problems = []
    declared = {it.get("id"): it for it in backlog}
    for cap, state in probes.items():
        is_declared = cap in declared
        if state["status"] == "implemented" and not state["delivered"]:
            problems.append(
                f"{cap}: 判为 implemented 但交付判据未命中（疑似门禁假绿），请检查探针"
            )
        if state["status"] == "implemented" and is_declared:
            problems.append(
                f"{cap}: 源码完成标志 {state['completionHits']} 处命中，却仍列在 backlog "
                f"({declared[cap].get('tier')})；请移出 backlog 或修正探针"
            )
        elif state["status"] != "implemented" and not is_declared:
            problems.append(
                f"{cap}: 源码状态 {state['status']}，backlog 却未登记，缺口失去跟踪"
            )
        if is_declared and state["status"] != "implemented":
            tracking = str(declared[cap].get("tracking") or "").strip().lower()
            if not tracking.startswith(state["status"]):
                problems.append(
                    f"{cap}: backlog tracking 声明与源码实测状态 {state['status']} 不一致，"
                    f"人工文本漂移（tracking={tracking[:40]!r}）"
                )
    for cap in declared:
        if cap not in probes:
            problems.append(f"{cap}: backlog 项缺少能力探针，无法由源码守护")
    return problems



def build_as_is_checks(probes: dict) -> list[dict]:
    sql = cl.read(INIT_SQL)
    return [
        {
            "id": "openapi-masterdata",
            "title": "masterdata-v1 OpenAPI",
            "status": "as_is",
            "evidence": {"openapi": cl.scan_openapi_paths(OPENAPI)},
        },
        {
            "id": "entity-record-api",
            "title": "实体/记录/字段/质量 REST",
            "status": "as_is",
            "evidence": {
                "java": cl.grep_files(
                    MAIN,
                    r"MasterDataEntityController|MasterDataRecordController|"
                    r"MasterDataFieldController|DataQualityController",
                    ROOT,
                ),
            },
        },
        {
            "id": "archunit",
            "title": "bone-masterdata ArchUnit",
            "status": "as_is",
            "evidence": {"java": cl.grep_files(TEST, r"ArchitectureTest", ROOT)},
        },
        {
            "id": "mdm-ddl",
            "title": "mdm_* 表在 bone-init",
            "status": "as_is",
            "evidence": {
                "mdm_entity": "mdm_entity" in sql or "mdm_master_entity" in sql,
                "any_mdm": "mdm_" in sql,
            },
        },
        {
            "id": "capability-probes",
            "title": "[Target]/[Vision] 能力源码探针",
            "status": "as_is",
            "evidence": {
                "probes": probes,
                "implemented": [k for k, v in probes.items() if v["status"] == "implemented"],
                "partial": [k for k, v in probes.items() if v["status"] == "partial"],
                "missing": [k for k, v in probes.items() if v["status"] == "missing"],
                "orchestrated": [k for k, v in probes.items() if v["orchestrated"]],
                "relayWired": [k for k, v in probes.items() if v["relayHits"] > 0],
                "delivered": [k for k, v in probes.items() if v["delivered"]],
            },
        },
        {
            "id": "governance-raci",
            "title": "Steward RACI / SoD 治理登记",
            "status": "as_is",
            "evidence": {
                "doc": str(GOVERNANCE_DOC.relative_to(ROOT)),
                "anchors": {
                    anchor: anchor in cl.read(GOVERNANCE_DOC)
                    for anchor in RACI_ANCHORS
                },
                "complete": all(
                    anchor in cl.read(GOVERNANCE_DOC) for anchor in RACI_ANCHORS
                ),
            },
        },
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sync-doc", action="store_true")
    args = parser.parse_args()
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    probes = scan_capabilities()
    checks = build_as_is_checks(probes)
    backlog = cl.load_backlog(COLLECTOR_DIR / "backlog.yaml")
    drift = assert_backlog_consistency(backlog, probes)
    if drift:
        for line in drift:
            print(f"[drift] {line}", file=sys.stderr)
        print(f"[drift] {len(drift)} 项 backlog 与源码不一致，请更新 backlog.yaml", file=sys.stderr)
        return 1
    if args.check:
        return 0 if cl.check_outputs(OUT_DIR, checks, backlog, "masterdata") else 1
    cl.write_outputs(
        OUT_DIR,
        "masterdata",
        "主数据",
        checks,
        backlog,
        generated_at,
        "../../../tools/masterdata-compliance-collector/backlog.yaml",
    )
    if args.sync_doc:
        ok = cl.sync_design_doc(
            DESIGN_DOC,
            AS_IS_MARKER,
            BACKLOG_MARKER,
            checks,
            backlog,
            "../../_generated/masterdata/compliance.json",
            "../../../tools/masterdata-compliance-collector/backlog.yaml",
        )
        if ok:
            print(f"[sync-doc] updated {DESIGN_DOC.relative_to(ROOT)}")
    print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
