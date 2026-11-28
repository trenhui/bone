#!/usr/bin/env python3
"""影像自动分类的 Python 镜像，规则与 Bone DocTypeClassifier 保持一致。

只用于回填脚本的“预期结果/解释”；真正落库的 DocType 由 Bone 服务端在登记或
reclassify 接口重新判定，避免脚本与后端口径漂移。证据不足时 fail-closed 落 OTHER，
不把风景照/签名照冒充医疗票据。

版式信号算法与 bone-frontend docTypeSignals.ts / DocTypeClassifier 一致：
160×120 灰度缩略图、偏离中位色 >35 的前景、8×6 网格前 4 格集中度、长横向游程表格线。
"""

from __future__ import annotations

import re
from pathlib import Path

# (DocType, regex, weight) —— 与 DocTypeClassifier.NAME_RULES 逐条对应。
NAME_RULES: list[tuple[str, str, float]] = [
    ("ID_CARD", r"身份证|身份|idcard|shenfenzheng", 3.0),
    ("BANK_CARD", r"银行卡|bankcard", 3.0),
    ("DISCHARGE_SUMMARY", r"出院小结|出院记录|出院证明|discharge", 3.0),
    ("MEDICAL_SETTLEMENT", r"结算单|医保|住院费用|settlement", 3.0),
    ("PRESCRIPTION", r"处方|prescription", 3.0),
    ("MEDICAL_RECORD", r"病历|诊疗|就诊记录|medicalrecord", 3.0),
    ("POLICY", r"保单|保险单|policy", 3.0),
    ("SIGNATURE", r"签名|签字|签章|手写|signature|sign", 3.0),
    # 「收费单」刻意不给 INVOICE：它是费用清单，不是发票。
    ("INVOICE", r"发票|票据|invoice|receipt|fapiao", 2.6),
    ("LIST", r"清单|费用明细|收费单|收费明细|felist|detail", 2.4),
]

MIN_SCORE = 2.0
MIN_MARGIN = 1.0
SAMPLE_W = 160
SAMPLE_H = 120

# 源站存在 opaque 文件名（AAAA.png / BBBB.png / 32 位 hash），文件名+版式信号不足以
# 判定单证类型。以下为逐张核实内容后登记的“内容指纹 → DocType”，按 SHA256 命中，
# 不按赔案号/文件名硬编码，同内容跨案件复用，避免风景照被冒充发票。
CONTENT_HASH_OVERRIDES: dict[str, str] = {
    "d72f1b67ac3bd51b976dec9a4474b64c03e8ce2fa4e3c1da5bd4e51e1689bb68": "INVOICE",  # AAAA 电子发票
    "6d3f774ae4bd194cc0c4c1cb03dbdb257f14c9ed988255c7a2bb75eec634f720": "INVOICE",  # BBBB 医疗收费票据
    "2e7b133dc7943aece6c6d49907c82fc90efab68f83f7f5028aae00d982c8043a": "OTHER",   # 风景照（源站脏数据）
    "899c4272fb8ee97990ce08ecd3edc9a34dae97c465da0eadebec15a81b7b25cb": "OTHER",   # 风景照
    "bd641f0e54154d72f9c28dbdf97ed1638a2aaf42b7afeb5af687dd612e343810": "OTHER",   # 动物照
    "7d4b69e8d25acadaeaa495e512bacbca87a706c3c589addc88e5732f97cc366a": "SIGNATURE",
    "e4225afe2339bdbe1e75042d639d56fd424550178d1c4456c325efe5a457bd37": "LIST",    # 理赔申请书按表单清单归类
}


def normalize(value: str | None) -> str:
    return re.sub(r"[\s_\-—.]+", "", (value or "").lower())


def extract_image_signals(path: Path) -> dict[str, float | int] | None:
    """从本地影像提取版式信号；缺 Pillow 或解码失败时返回 None（后端退化为文件名分类）。"""
    try:
        from PIL import Image  # 本地取证依赖，非 Bone 运行时依赖
    except ImportError:
        return None
    try:
        with Image.open(path) as src:
            thumb = src.convert("RGB").resize((SAMPLE_W, SAMPLE_H), Image.LANCZOS)
            pixels = list(thumb.getdata())
        gray = [round(0.299 * r + 0.587 * g + 0.114 * b) for r, g, b in pixels]
        median = sorted(gray)[len(gray) // 2]
        fg = [1 if abs(v - median) > 35 else 0 for v in gray]
        ink_ratio = sum(fg) / len(fg)

        cell_w = SAMPLE_W / 8
        cell_h = SAMPLE_H / 6
        cells: list[int] = []
        for gy in range(6):
            for gx in range(8):
                cells.append(
                    sum(
                        fg[y * SAMPLE_W + x]
                        for y in range(int(gy * cell_h), int((gy + 1) * cell_h))
                        for x in range(int(gx * cell_w), int((gx + 1) * cell_w))
                    )
                )
        total_ink = sum(cells)
        top4 = sum(sorted(cells, reverse=True)[:4])
        concentration = top4 / total_ink if total_ink else 0.0

        line_rows = 0
        for y in range(SAMPLE_H):
            run = longest = 0
            for x in range(SAMPLE_W):
                run = fg[y * SAMPLE_W + x] and run + 1 or 0
                longest = max(longest, run)
            if longest >= int(SAMPLE_W * 0.375):
                line_rows += 1
        with Image.open(path) as src:
            width, height = src.size
        return {
            "inkRatio": round(ink_ratio, 6),
            "inkConcentration": round(concentration, 6),
            "tableLineRatio": round(line_rows / SAMPLE_H, 6),
            "aspectPercent": round(height / width * 100) if width else 0,
        }
    except Exception:
        return None


def classify(
    original_name: str | None, image_type: str | int | None,
    signals: dict[str, float | int] | None,
) -> tuple[str, float, float, str]:
    """返回 (DocType, 最高分, 领先分, 命中原因)；与后端融合规则同阈值。"""
    scores: dict[str, float] = {}
    reasons: list[str] = []
    name = normalize(original_name)
    for doc_type, pattern, weight in NAME_RULES:
        if re.search(pattern, name):
            scores[doc_type] = scores.get(doc_type, 0.0) + weight
            reasons.append(f"文件名命中{doc_type}")

    if signals and signals.get("inkRatio") is not None:
        ink = float(signals["inkRatio"])
        conc = float(signals.get("inkConcentration") or 0)
        table = float(signals.get("tableLineRatio") or 0)
        if ink < 0.16 and conc >= 0.50 and table < 0.04:
            scores["SIGNATURE"] = scores.get("SIGNATURE", 0.0) + 4.0
            reasons.append("版式=低墨迹高集中无表格")
        elif table >= 0.05 and conc < 0.50:
            scores["INVOICE"] = scores.get("INVOICE", 0.0) + 1.2
            scores["LIST"] = scores.get("LIST", 0.0) + 1.2
            reasons.append("版式=含表格线且墨迹分散")

    # TPA imageType=3017 才是发票弱证据；=5 是通用影像，不作为分类依据。
    if str(image_type or "").strip() == "3017":
        scores["INVOICE"] = scores.get("INVOICE", 0.0) + 1.0
        reasons.append("TPA imageType=3017")

    if not scores:
        return "OTHER", 0.0, 0.0, "无有效证据"
    best = max(scores, key=lambda key: scores[key])
    top = scores[best]
    second = sorted(scores.values(), reverse=True)[1] if len(scores) > 1 else 0.0
    margin = top - second
    if top < MIN_SCORE or margin < MIN_MARGIN:
        return "OTHER", top, margin, "；".join(reasons) + "（证据不足/并列）"
    return best, top, margin, "；".join(reasons)


def resolve(
    original_name: str | None,
    digest: str | None,
    image_type: str | int | None,
    signals: dict[str, float | int] | None,
) -> tuple[str, str, bool]:
    """返回 (最终 DocType, 解释, 是否需显式指定)。

    服务端融合分类优先；内容指纹/申请书规则命中时由回填脚本显式传 docType，
    因为服务端 DocTypeClassifier 对这些源站特例仍会 fail-closed 落 OTHER。
    """
    if digest and digest in CONTENT_HASH_OVERRIDES:
        return CONTENT_HASH_OVERRIDES[digest], f"内容指纹已核实({digest[:8]})", True
    if "理赔申请书" in (original_name or ""):
        return "LIST", "理赔申请书按表单清单归类", True
    doc_type, _, _, why = classify(original_name, image_type, signals)
    return doc_type, why, False
