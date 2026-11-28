#!/usr/bin/env python3
"""一次性补全全部 TPA 签收案件：源站字段 + 全量影像 + 自动分类（可重跑）。

覆盖 signRecord_members.json 中的 15 个源站赔案（14 个已建案 + 1 个 AWAIT_MANUAL）。
默认 --dry-run 只打印计划；--apply 才写本地开发库与调用 Bone API。

写入纪律：
  · 字段只补空（COALESCE / NULLIF），不覆盖已有人工值；
  · 影像按 contentHash 幂等；已有 evidence 通过 reclassify 接口按同一分类器重判；
  · 可读文件名/版式信号走服务端融合分类；opaque 文件名按内容 SHA256 指纹归类；
  · 源站 0 影像的案件登记 NO_SOURCE_IMAGES，不伪造；无本地案件的只补签收明细字段。
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path

from fetch_sign_all import Client as TpaClient
from fetch_sign_images import (
    BONE,
    BONE_FILE,
    ROOT,
    bone_api,
    bone_login,
    case_map,
    fetch_all,
    load_env,
    upload_file,
)
from sign_image_classifier import extract_image_signals, resolve


def now_cn() -> str:
    from datetime import timedelta, timezone

    return datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S +0800")


def parse_dt(value):
    if not value:
        return None
    try:
        return datetime.strptime(str(value)[:19], "%Y-%m-%d %H:%M:%S")
    except ValueError:
        return None


def text(value, limit: int | None = None):
    if value is None:
        return None
    out = str(value)
    return out[:limit] if limit else out


def stable_id(prefix: str) -> int:
    """幂等 BIGINT ID：SHA256 前 8 字节清最高位，避开雪花段与既有固定 ID。"""
    digest = hashlib.sha256(prefix.encode("utf-8")).digest()[:8]
    return int.from_bytes(digest, "big") & 0x7FFFFFFFFFFFFFFF


def source_claims() -> tuple[list[str], dict[str, dict]]:
    members = json.loads((ROOT / "signRecord_members.json").read_text(encoding="utf-8"))
    claims: dict[str, dict] = {}
    for block in members.values():
        for row in block.get("members") or []:
            cid = str((row or {}).get("id") or "")
            if cid.isdigit():
                claims[cid] = row
    return sorted(claims), claims


def fetch_details(client: TpaClient, claims: list[str]) -> dict[str, dict]:
    out: dict[str, dict] = {}
    for cid in claims:
        detail = client.get(f"/data-api/tpa/claim/detail?id={cid}").get("data") or {}
        out[cid] = detail
        print("DETAIL", cid, "keys=", len(detail))
    (ROOT / "sign_case_details.json").write_text(
        json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return out


def backfill_fields(details: dict[str, dict], members: dict[str, dict],
                    images: dict[str, list[dict]], dry_run: bool) -> dict:
    import pymysql

    env = load_env()
    conn = pymysql.connect(
        host=env["BONE_DB_HOST"], port=int(env["BONE_DB_PORT"]),
        user=env["BONE_DB_USERNAME"], password=env["BONE_DB_PASSWORD"],
        database=env["BONE_DB_NAME"], charset="utf8mb4",
    )
    summary: dict[str, dict] = {}
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id,claim_no FROM t_claim_case WHERE tenant_id=0 AND claim_no IN (%s)"
                % ",".join(["%s"] * len(details)),
                list(details),
            )
            case_by_no = {str(no): int(cid) for no, cid in cur.fetchall()}
            cur.execute("SELECT id FROM t_claim_stakeholder WHERE tenant_id=0")
            stakeholder_ids = {int(r[0]) for r in cur.fetchall()}
            cur.execute("SELECT claim_id FROM t_claim_stakeholder WHERE role='INSURED' AND tenant_id=0")
            stakeholder_claims = {int(r[0]) for r in cur.fetchall()}
            cur.execute("SELECT id FROM t_claim_item WHERE tenant_id=0")
            claim_item_ids = {int(r[0]) for r in cur.fetchall()}
            cur.execute(
                "SELECT claim_id FROM t_claim_item WHERE item_code='SIGN-IMPORT' AND tenant_id=0"
            )
            placeholder_claims = {int(r[0]) for r in cur.fetchall()}

            cur.execute(
                "SELECT id,claim_no,created_case_id FROM t_claim_sign_batch_item "
                "WHERE tenant_id=0 AND claim_no IN (%s)"
                % ",".join(["%s"] * len(details)),
                list(details),
            )
            rows = cur.fetchall()
            for item_id, claim_no, created_case_id in rows:
                case_id = int(created_case_id) if created_case_id else case_by_no.get(str(claim_no))
                detail = details[claim_no]
                member = members.get(claim_no) or {}
                person = detail.get("outInsurePerson") or {}
                collect = detail.get("collectInfo") or {}
                adjust = detail.get("adjustmentResult") or {}
                image_rows = images.get(claim_no) or []
                values = {
                    "image_count": int(detail.get("imageCount") or len(image_rows)),
                    "image_upload_count": detail.get("imageUploadCount"),
                    "image_upload_time": parse_dt(detail.get("imageUploadTime")),
                    "image_uploader": text(detail.get("imageUploader"), 64),
                    "identity_type": text(
                        person.get("outInsureIdentityTypeCn") or member.get("outInsureIdentityType"), 32
                    ),
                    "counting_result": text(detail.get("countingResult"), 64),
                }
                applied = {}
                if not dry_run:
                    cur.execute(
                        """UPDATE t_claim_sign_batch_item SET
                           image_count=COALESCE(image_count,%s),
                           image_upload_count=COALESCE(image_upload_count,%s),
                           image_upload_time=COALESCE(image_upload_time,%s),
                           image_uploader=COALESCE(image_uploader,%s),
                           identity_type=COALESCE(identity_type,%s),
                           counting_result=COALESCE(counting_result,%s),
                           updated_at=%s WHERE id=%s""",
                        (*values.values(), datetime.now(), item_id),
                    )
                    applied["sign_item"] = cur.rowcount

                if case_id and adjust.get("payOutAmount") is not None:
                    if not dry_run:
                        cur.execute(
                            "UPDATE t_claim_case SET claim_amount=COALESCE(claim_amount,%s) "
                            "WHERE id=%s AND tenant_id=0",
                            (adjust.get("payOutAmount"), case_id),
                        )
                        applied["case_amount"] = cur.rowcount
                if case_id:
                    party = {
                        "collect_type": text(collect.get("collectType"), 32),
                        "phone_masked": text(person.get("outInsurePhone"), 64),
                        "relation": text(person.get("outRelationToMainInsureCn"), 64),
                    }
                    party = {k: v for k, v in party.items() if v}
                    if party and not dry_run:
                        cur.execute(
                            """UPDATE t_claim_stakeholder SET
                               collect_type=COALESCE(NULLIF(collect_type,''),%s),
                               phone_masked=COALESCE(NULLIF(phone_masked,''),%s),
                               relation=COALESCE(NULLIF(relation,''),%s),
                               updated_at=%s
                               WHERE claim_id=%s AND role='INSURED' AND tenant_id=0""",
                            (party.get("collect_type"), party.get("phone_masked"),
                             party.get("relation"), datetime.now(), case_id),
                        )
                        applied["stakeholder"] = cur.rowcount

                    insured_name = text(
                        person.get("outInsureName") or member.get("outInsureName"), 128
                    )
                    insured_id = text(
                        person.get("outInsureIdentityNo") or member.get("outInsureIdentityNo"), 64
                    )
                    if insured_name and insured_id and case_id not in stakeholder_claims:
                        sid = stable_id(f"pk-tpa-sign-stakeholder-{claim_no}")
                        if not dry_run:
                            cur.execute(
                                """INSERT INTO t_claim_stakeholder
                                   (id,claim_id,role,person_id_hash,name_masked,relation,
                                    phone_masked,collect_type,bank_ref,tenant_id,created_by,
                                    created_at,updated_by,updated_at,version,deleted)
                                   VALUES (%s,%s,'INSURED',%s,%s,%s,%s,%s,'',0,1,%s,1,%s,0,0)""",
                                (sid, case_id, insured_id, insured_name,
                                 party.get("relation") or "", party.get("phone_masked") or "",
                                 party.get("collect_type") or "", datetime.now(), datetime.now()),
                            )
                            stakeholder_ids.add(sid)
                            applied["stakeholder_created"] = cur.rowcount
                        stakeholder_claims.add(case_id)

                    if case_id not in placeholder_claims:
                        iid = stable_id(f"pk-tpa-sign-claim-item-{claim_no}")
                        if not dry_run:
                            cur.execute(
                                """INSERT INTO t_claim_item
                                   (id,claim_id,item_name,item_code,category,quantity,unit_price,
                                    amount,medical_type,valid_amount,tenant_id,created_at,
                                    updated_at,version,deleted)
                                   VALUES (%s,%s,'签收导入占位费用','SIGN-IMPORT','SERVICE',
                                           1,0,0,'',0,0,%s,%s,0,0)""",
                                (iid, case_id, datetime.now(), datetime.now()),
                            )
                            claim_item_ids.add(iid)
                            applied["claim_item_created"] = cur.rowcount
                        placeholder_claims.add(case_id)

                summary[claim_no] = {
                    "signItemId": item_id, "caseId": case_id,
                    "fields": values, "applied": applied,
                }
        if not dry_run:
            conn.commit()
        return summary
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def complete_images(token: str, claims: list[str], images: dict[str, list[dict]],
                    local_cases: dict[str, int], dry_run: bool) -> dict:
    # 全局内容哈希索引：019001 的案件里复用了 023001 的影像，不能只按本案件查。
    by_hash: dict[str, dict] = {}
    for cid, rows in images.items():
        for row in rows:
            if row.get("sha256"):
                by_hash[row["sha256"]] = {"claimNo": cid, **row}

    object_by_hash: dict[str, str] = {}
    result: dict[str, dict] = {}
    for cid in claims:
        case_id = local_cases.get(cid)
        rows = images[cid]
        entry: dict[str, object] = {"caseId": case_id, "sourceImages": len(rows), "items": []}
        if case_id is None:
            entry["note"] = "NO_LOCAL_CASE：只补签收明细字段，不建案不登记影像"
        elif not rows:
            entry["note"] = "NO_SOURCE_IMAGES：源站无影像，不伪造"
        evidence_before: list[dict] = []
        if case_id and not dry_run:
            status, body = bone_api(token, f"/api/v1/claims/{case_id}/evidences")
            if status != 200:
                raise RuntimeError(f"查询影像失败 case={case_id} status={status}")
            evidence_before = body.get("data") or []
        known = {str(x.get("contentHash")) for x in evidence_before}
        entry["existingBefore"] = len(known)

        for row in rows:
            digest = str(row.get("sha256") or "")
            name = str(row.get("imageName") or "")
            signals = extract_image_signals(ROOT / row["localFile"]) if row.get("downloaded") else None
            doc_type, why, force_type = resolve(name, digest, row.get("imageType"), signals)
            item: dict[str, object] = {
                "imageName": name, "docType": doc_type, "sha256": digest,
                "classifyReason": why, "downloaded": bool(row.get("downloaded")),
            }
            if not row.get("downloaded"):
                item["status"] = "DOWNLOAD_FAILED"
            elif dry_run:
                item["status"] = "WOULD_REGISTER" if digest not in known else "WOULD_SKIP_DUPLICATE"
            elif digest in known:
                item["status"] = "ALREADY_REGISTERED"
            elif case_id is None:
                item["status"] = "NO_LOCAL_CASE"
            else:
                object_name = object_by_hash.get(digest) or upload_file(token, ROOT / row["localFile"])
                object_by_hash[digest] = object_name
                request: dict[str, object] = {
                    "fileRef": object_name, "contentHash": digest, "verifyValid": "UNKNOWN",
                    "originalName": name, "imageType": str(row.get("imageType") or ""),
                    "imageSignals": signals,
                }
                if force_type:
                    request["docType"] = doc_type
                else:
                    request["autoClassify"] = True
                status, body = bone_api(
                    token, f"/api/v1/claims/{case_id}/evidences", request, method="POST",
                    idem=f"pk-tpa-sign-img-{cid}-{digest[:16]}"[:64],
                )
                item.update({
                    "objectName": object_name, "httpStatus": status,
                    "status": "REGISTERED" if body.get("success") else "FAILED",
                    "message": body.get("message"),
                })
                print("REGISTER", cid, "->", case_id, name, doc_type, status)
            entry["items"].append(item)

        # 存量 evidence 统一重判：按内容哈希找回源站文件名与版式信号。
        reclassify_items = []
        for ev in evidence_before:
            source = by_hash.get(str(ev.get("contentHash")))
            if not source:
                continue
            signals = extract_image_signals(ROOT / source["localFile"])
            doc_type, why, force_type = resolve(
                source["imageName"], source.get("sha256"), source.get("imageType"), signals
            )
            payload: dict[str, object] = {
                "evidenceId": int(ev["id"]),
                "originalName": source["imageName"],
                "imageType": str(source.get("imageType") or ""),
                "imageSignals": signals,
            }
            if force_type:
                payload["docType"] = doc_type
            reclassify_items.append({
                "payload": payload, "before": ev.get("docType"), "after": doc_type,
                "reason": why, "name": source["imageName"],
            })
        if reclassify_items and not dry_run:
            status, body = bone_api(
                token, f"/api/v1/claims/{case_id}/evidences/reclassify",
                {"items": [x["payload"] for x in reclassify_items]}, method="POST",
            )
            entry["reclassify"] = {
                "httpStatus": status, "updated": (body.get("data") or 0),
                "items": [{k: v for k, v in x.items() if k != "payload"} for x in reclassify_items],
            }
        elif reclassify_items:
            entry["reclassify"] = {
                "dryRun": True,
                "items": [{k: v for k, v in x.items() if k != "payload"} for x in reclassify_items],
            }
        result[cid] = entry
    return result


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写入；缺省仅 dry-run")
    args = ap.parse_args()

    claims, members = source_claims()
    print("SOURCE_CLAIMS", len(claims))
    client = TpaClient()
    details = fetch_details(client, claims)
    images = fetch_all(client, claims)
    local_cases = case_map()
    print("LOCAL_CASES", sum(1 for c in claims if c in local_cases))

    field_summary = backfill_fields(details, members, images, not args.apply)
    token = "" if not args.apply else bone_login()
    image_summary = complete_images(token, claims, images, local_cases, not args.apply)

    result = {
        "completedAt": now_cn(), "dryRun": not args.apply,
        "claims": len(claims), "localCases": len(local_cases),
        "fields": field_summary, "images": image_summary,
    }
    out = ROOT / "sign_case_completion_result.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    registered = sum(
        1 for entry in image_summary.values()
        for item in entry.get("items") or [] if item.get("status") == "REGISTERED"
    )
    reclassified = sum(
        int((entry.get("reclassify") or {}).get("updated") or 0)
        for entry in image_summary.values()
    )
    print("RESULT", out, "REGISTERED", registered, "RECLASSIFIED", reclassified)


if __name__ == "__main__":
    main()
