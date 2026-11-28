#!/usr/bin/env python3
"""为签收自动生成的案件补齐 TPA 源站影像件（只读抓取 + 本地正规上传，可重跑）。

用法：
    python3 scripts/claim_sign_backfill/fetch_sign_images.py --dry-run   # 只抓取并打印计划
    python3 scripts/claim_sign_backfill/fetch_sign_images.py             # 抓取并上传登记

数据链路：
  1. 从 signRecord_members.json 取签收明细里的 TPA 赔案号；
  2. 用 UI 登录的 Sa-Token Cookie 调 /data-api/tpa/image/query 与 OSS 下载地址，
     把影像落到 outputs/tpa-claim-crawl/sign_images/case_<赔案号>/（本地证据，不入库）；
  3. 查 t_claim_sign_batch_item.created_case_id 找本地案件；
  4. 走网关 POST /api/v1/file/files 上传 bone-file，再 POST /api/v1/claims/{id}/evidences
     登记 fileRef + contentHash；按 contentHash 幂等，已有影像不重复登记。

分类纪律：imagePkType=5 在 TPA 是「其他资料」大杂烩，不能当 DocType。逐张按源站
imageName + 实际内容人工核实后写死在下表；未收录的一律落 OTHER（fail-closed），
绝不把风景照/动物照冒充医疗票据。源站没有影像的案件登记 NO_SOURCE_IMAGES，不造假。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "outputs" / "tpa-claim-crawl"
ENV = Path(__file__).resolve().parents[2] / ".env"
TPA = "https://tpa-test.pukangbao.com"
BONE = "http://127.0.0.1:18888"
# 网关 18888 转发 multipart 大文件会卡住（实测 2MB/60s 只上传 558KB 后超时），
# bone-file 8107 直连 0.4s 完成；两者共用同一 JWT 密钥，故上传走直连、业务登记走网关。
BONE_FILE = "http://127.0.0.1:8107"

# (赔案号, 源站 imageName) -> Bone DocType；逐张按影像内容核实。
DOC_TYPE_OVERRIDES: dict[tuple[str, str], str] = {
    # 264203068 四案共用两张收费票据：AAAA 为电子发票（普通发票），BBBB 为广东省医疗收费票据。
    ("264203068001", "AAAA.png"): "INVOICE",
    ("264203068001", "BBBB.png"): "INVOICE",
    ("264203068002", "AAAA.png"): "INVOICE",
    ("264203068002", "BBBB.png"): "INVOICE",
    ("264203068003", "AAAA.png"): "INVOICE",
    ("264203068003", "BBBB.png"): "INVOICE",
    ("264203068004", "AAAA.png"): "INVOICE",
    ("264203068004", "BBBB.png"): "INVOICE",
    # 266400020008：6 张里 3 张是风景/动物照（源站测试数据污染），按 OTHER 原样登记。
    ("266400020008", "641.BMP"): "OTHER",
    ("266400020008", "4081c418b456da14b12e3d59d67188d5.jpeg"): "OTHER",
    ("266400020008", "3fe6db1a8d291be39192f9a06c74ce99.png"): "OTHER",
    ("266400020008", "1CA9AC0A80B895BFF0524A577696E054.jpg"): "SIGNATURE",
    ("266400020008", "3-泰瑞人寿保险-理赔申请书.jpg"): "LIST",
    ("266400020008", "2-河南医疗门诊收费票据.jpg"): "INVOICE",
    # 以下三案已有 evidence，登记时按 contentHash 自动跳过；映射保留以便重跑/换库。
    ("266400019001", "1CA9AC0A80B895BFF0524A577696E054.jpg"): "SIGNATURE",
    ("266400023001", "7-身份证-正面-00.jpg"): "ID_CARD",
    ("266400023001", "6-病历本.jpg"): "MEDICAL_RECORD",
    ("266400023001", "3-泰瑞人寿保险-理赔申请书.jpg"): "LIST",
    ("266400023002", "7-身份证-正面-00.jpg"): "ID_CARD",
    ("266400023002", "6-病历本.jpg"): "MEDICAL_RECORD",
    ("266400023002", "3-泰瑞人寿保险-理赔申请书.jpg"): "LIST",
}


class TpaClient:
    """data-api 只认 Sa-Token Cookie，Bearer 返回 401。"""

    def __init__(self) -> None:
        req = urllib.request.Request(
            TPA + "/admin-api/system/auth/login",
            data=json.dumps({"username": "admin", "password": "123456"}).encode(),
            headers={"Content-Type": "application/json", "tenant-id": "1"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=40) as resp:
            payload = json.loads(resp.read().decode())
        if not payload.get("success"):
            raise RuntimeError(f"TPA 登录失败: {payload}")
        self.cookie = "Satoken=" + payload["data"]

    def post(self, path: str, payload: dict) -> dict:
        req = urllib.request.Request(
            TPA + path,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Cookie": self.cookie,
                "tenant-id": "1",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read().decode("utf-8", "replace"))


def now_cn() -> str:
    from datetime import datetime, timedelta, timezone

    return datetime.now(timezone(timedelta(hours=8))).strftime("%Y-%m-%d %H:%M:%S +0800")


def safe_name(value: str) -> str:
    return re.sub(r"[^0-9A-Za-z._-]+", "_", value)[:80].strip("_") or "image"


def load_env() -> dict:
    out: dict[str, str] = {}
    for line in ENV.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def case_map() -> dict[str, int]:
    """签收赔案号 -> 本地案件 ID（只取已建案的明细，忽略 AWAIT_MANUAL）。"""
    import pymysql

    env = load_env()
    conn = pymysql.connect(
        host=env["BONE_DB_HOST"], port=int(env["BONE_DB_PORT"]),
        user=env["BONE_DB_USERNAME"], password=env["BONE_DB_PASSWORD"],
        database=env["BONE_DB_NAME"], charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT claim_no, created_case_id FROM t_claim_sign_batch_item "
                "WHERE tenant_id = 0 AND created_case_id IS NOT NULL"
            )
            return {str(r[0]): int(r[1]) for r in cur.fetchall()}
    finally:
        conn.close()


def bone_login() -> str:
    req = urllib.request.Request(
        BONE + "/api/v1/iam/login",
        data=b'{"username":"admin","password":"123456"}',
        headers={"Content-Type": "application/json"},
    )
    return json.load(urllib.request.urlopen(req, timeout=20))["data"]["token"]


def bone_api(token: str, path: str, payload: dict | None = None,
             method: str = "GET", idem: str | None = None) -> tuple[int, dict]:
    headers = {"Authorization": "Bearer " + token}
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if idem:
        headers["Idempotency-Key"] = idem
    req = urllib.request.Request(BONE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=40) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode("utf-8", "replace") or "{}")


def upload_file(token: str, path: Path) -> str:
    mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    out = subprocess.run(
        [
            "curl", "-sS", "--noproxy", "*", "-X", "POST", BONE_FILE + "/api/v1/file/files",
            "-H", "Authorization: Bearer " + token,
            "-F", f"file=@{path};filename={path.name};type={mime}",
            "--max-time", "90",
        ],
        capture_output=True, text=True, check=True,
    ).stdout
    body = json.loads(out)
    if not body.get("success"):
        raise RuntimeError(f"bone-file 上传失败: {body.get('message')}")
    return body["data"]["objectName"]


def fetch_all(client: TpaClient, claims: list[str]) -> dict[str, list[dict]]:
    """拉源站影像清单并下载；索引与文件落 outputs，供后续上传与复核。"""
    index_dir = ROOT / "sign_images"
    index_dir.mkdir(parents=True, exist_ok=True)
    result: dict[str, list[dict]] = {}
    for cid in claims:
        payload = client.post(
            "/data-api/tpa/image/query",
            {"id": cid, "tenantId": "1", "pageNo": 0, "pageSize": 0},
        )
        rows = payload.get("data") or []
        folder = index_dir / f"case_{cid}"
        folder.mkdir(parents=True, exist_ok=True)
        items: list[dict] = []
        for i, row in enumerate(rows, 1):
            name = row.get("imageName") or f"image_{i}"
            url = row.get("imagePath") or ""
            target = folder / f"{i:02d}__{safe_name(Path(name).stem)}{Path(urllib.parse.urlparse(url).path).suffix or '.jpg'}"
            item = {
                "sourceImageId": row.get("id"),
                "imageName": name,
                "imageType": row.get("imagePkType"),
                "imagePath": url,
                "localFile": str(target.relative_to(ROOT)),
            }
            if url:
                try:
                    with urllib.request.urlopen(urllib.parse.quote(url, safe=":/?&=%"), timeout=90) as resp:
                        blob = resp.read()
                    target.write_bytes(blob)
                    item.update({
                        "downloaded": True,
                        "bytes": len(blob),
                        "sha256": hashlib.sha256(blob).hexdigest(),
                    })
                except Exception as exc:  # 下载失败留证据，不伪造影像
                    item.update({"downloaded": False, "error": str(exc)[:200]})
            else:
                item.update({"downloaded": False, "error": "源站 imagePath 为空"})
            items.append(item)
        result[cid] = items
        print(f"FETCH {cid} images={len(items)} downloaded={sum(1 for x in items if x.get('downloaded'))}")
    (ROOT / "sign_images_index.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return result


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只抓取与打印计划，不上传登记")
    args = ap.parse_args()

    members = json.loads((ROOT / "signRecord_members.json").read_text(encoding="utf-8"))
    claims = sorted({
        str((x or {}).get("id") or "")
        for block in members.values()
        for x in block.get("members") or []
        if str((x or {}).get("id") or "").isdigit()
    })
    print("CLAIMS", len(claims))
    images = fetch_all(TpaClient(), claims)
    local_cases = case_map()
    result = {
        "fetchedAt": now_cn(),
        "source": TPA + "/jobManage/groupInsuranceSignList",
        "bone": BONE,
        "dryRun": args.dry_run,
        "claims": {},
    }
    token = "" if args.dry_run else bone_login()
    object_by_hash: dict[str, str] = {}
    for cid in claims:
        case_id = local_cases.get(cid)
        rows = images[cid]
        entry: dict = {
            "caseId": case_id,
            "sourceImageCount": len(rows),
            "items": [],
        }
        if case_id is None:
            entry["note"] = "本地无已建案件（AWAIT_MANUAL 或未被签收明细引用），跳过登记"
        elif not rows:
            entry["note"] = "NO_SOURCE_IMAGES：源站该赔案没有影像，不伪造"
        known: set[str] = set()
        if case_id and not args.dry_run:
            status, body = bone_api(token, f"/api/v1/claims/{case_id}/evidences")
            if status != 200:
                raise RuntimeError(f"查询已有影像失败: case={case_id} status={status}")
            known = {str(x.get("contentHash")) for x in (body.get("data") or [])}
        entry["existingBefore"] = len(known)
        for row in rows:
            digest = str(row.get("sha256") or "")
            name = str(row.get("imageName") or "")
            doc_type = DOC_TYPE_OVERRIDES.get((cid, name), "OTHER")
            item = {
                "imageName": name,
                "sourceImageId": row.get("sourceImageId"),
                "docType": doc_type,
                "sha256": digest,
                "downloaded": bool(row.get("downloaded")),
            }
            if not row.get("downloaded"):
                item["status"] = "DOWNLOAD_FAILED"
            elif digest in known:
                item["status"] = "SKIPPED_DUPLICATE_HASH"
            elif args.dry_run:
                item["status"] = "WOULD_REGISTER"
            elif case_id is None:
                item["status"] = "NO_LOCAL_CASE"
            else:
                path = ROOT / row["localFile"]
                object_name = object_by_hash.get(digest) or upload_file(token, path)
                object_by_hash[digest] = object_name
                # Idempotency-Key 必须是 ASCII（HTTP 头 latin-1），源站 imageName 含中文，
                # 因此用赔案号 + 内容哈希生成稳定键。
                idem = f"pk-tpa-sign-img-{cid}-{digest[:16]}"[:64]
                status, body = bone_api(
                    token,
                    f"/api/v1/claims/{case_id}/evidences",
                    {"fileRef": object_name, "contentHash": digest,
                     "docType": doc_type, "verifyValid": "UNKNOWN"},
                    method="POST", idem=idem,
                )
                item.update({
                    "objectName": object_name,
                    "httpStatus": status,
                    "success": bool(body.get("success")),
                    "status": "REGISTERED" if body.get("success") else "FAILED",
                    "message": body.get("message"),
                })
                print("REGISTER", cid, "->", case_id, name, doc_type, status)
            entry["items"].append(item)
        result["claims"][cid] = entry
    out = ROOT / "sign_image_upload_result.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    registered = sum(
        1 for e in result["claims"].values() for i in e["items"] if i["status"] == "REGISTERED"
    )
    print("RESULT", out, "REGISTERED", registered)


if __name__ == "__main__":
    main()
