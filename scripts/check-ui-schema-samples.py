#!/usr/bin/env python3
"""UI Schema 样例契约校验（治理：schema 合规 + 合规文案引 key）。

校验 doc/design/lowcode/cases/ui-schema-samples/ 下所有 *.json：
1. 结构/类型合规：尽量用 jsonschema 对照 bone-ui-schema.schema.json 校验
   （缺 jsonschema 时降级为结构断言，仅校验 schemaVersion/entityCode/views 必需字段）；
2. 治理规则：footer.disclaimer 必须配 disclaimerKey（合规文案禁止裸奔内联 → 阻断）；
   notice / message / confirm.content 建议配对应 *Key（缺则 WARNING，不阻断）。

注意：本脚本为独立校验，不接入 scripts/check.sh（CI 脚本变更需 L3 审批）。
用法: python3 scripts/check-ui-schema-samples.py
"""
import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SCHEMA = REPO / "doc/design/lowcode/ui/ui-protocol/bone-ui-schema.schema.json"
SAMPLES = REPO / "doc/design/lowcode/cases/ui-schema-samples"


def load(p: Path):
    return json.loads(p.read_text(encoding="utf-8"))


def collect_dicts(node, out):
    if isinstance(node, dict):
        out.append(node)
        for v in node.values():
            collect_dicts(v, out)
    elif isinstance(node, list):
        for v in node:
            collect_dicts(v, out)


def main() -> int:
    if not SCHEMA.is_file():
        print(f"❌ 协议 schema 缺失: {SCHEMA}")
        return 1
    schema = load(SCHEMA)
    try:
        import jsonschema  # type: ignore

        validator = jsonschema.Draft7Validator(schema)
        have_jsonschema = True
    except Exception:
        have_jsonschema = False

    files = sorted(SAMPLES.glob("*.json"))
    if not files:
        print(f"❌ 样例目录无 json: {SAMPLES}")
        return 1

    errors = 0
    for f in files:
        try:
            data = load(f)
        except Exception as e:  # noqa: BLE001
            print(f"❌ {f.name}: JSON 解析失败 {e}")
            errors += 1
            continue

        # 1) schema 校验
        if have_jsonschema:
            errs = sorted(validator.iter_errors(data), key=lambda e: list(e.path))
            if errs:
                print(f"❌ {f.name}: schema 不合规 ({len(errs)} 处)")
                for e in errs[:10]:
                    loc = "/".join(str(x) for x in e.path) or "<root>"
                    print(f"   - {loc}: {e.message}")
                errors += 1
            else:
                print(f"✅ {f.name}: schema 合规")
        else:
            miss = [r for r in ("schemaVersion", "entityCode", "views") if r not in data]
            if miss:
                print(f"❌ {f.name}: 缺少必需字段 {miss}")
                errors += 1
            else:
                print(f"⚠️ {f.name}: jsonschema 不可用，仅结构断言（建议 pip install jsonschema）")

        # 2) 治理：合规文案引 key
        dicts: list[dict] = []
        collect_dicts(data, dicts)
        for d in dicts:
            if "disclaimer" in d and "disclaimerKey" not in d:
                print(f"❌ {f.name}: footer.disclaimer 缺少 disclaimerKey（合规文案禁止裸奔内联）")
                errors += 1
            if "notice" in d and "noticeKey" not in d:
                print(f"⚠️ {f.name}: 内联 notice 未配 noticeKey（建议引 key）")
            if "message" in d and "messageKey" not in d:
                print(f"⚠️ {f.name}: 内联 message 未配 messageKey（建议引 key）")
            if "content" in d and "contentKey" not in d:
                print(f"⚠️ {f.name}: confirm.content 未配 contentKey（建议引 key）")

    if errors:
        print(f"\n❌ 校验失败：{errors} 处阻断错误")
        return 1
    print("\n✅ 全部样例通过（含治理规则：合规文案均引 key）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
