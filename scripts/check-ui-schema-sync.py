#!/usr/bin/env python3
"""UI 协议契约同步校验（技术方案 §2.5）。

判据（三方）：
1. doc 真源与后端 classpath 拷贝的 SHA-256 必须一致 —— 防“文档改了服务端没跟”；
2. 前端 lint 白名单 PROP_WHITELIST（bone-metadata-app/.../uiSchema/constants.ts）必须与
   doc schema 的 definitions/control/properties/props 逐项一致 —— 防“协议改了自检没跟”
   （反向案例：2026-10-14 产品界面自检误报 9 错即为白名单滞后）。

当前为独立脚本；接入 scripts/check.sh 需 L3 审批（CI 脚本变更）， wiring 前请走架构师评审。
用法: python3 scripts/check-ui-schema-sync.py
"""

import hashlib
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DOC = REPO / "doc/design/lowcode/ui/ui-protocol/bone-ui-schema.schema.json"
CLASSPATH = REPO / "bone-engine/bone-metadata-server/src/main/resources/ui/bone-ui-schema.schema.json"
FRONT_CONSTANTS = REPO / "bone-frontend/apps/bone-metadata-app/src/components/uiSchema/constants.ts"


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def front_whitelist() -> set[str]:
    m = re.search(
        r"export const PROP_WHITELIST\s*=\s*\[([\s\S]*?)\]\s*as const",
        FRONT_CONSTANTS.read_text(encoding="utf-8"),
    )
    if not m:
        raise ValueError(f"未在 {FRONT_CONSTANTS} 找到 PROP_WHITELIST 定义")
    return set(re.findall(r"'([^']+)'", m.group(1)))


def main() -> int:
    missing = [str(p) for p in (DOC, CLASSPATH, FRONT_CONSTANTS) if not p.is_file()]
    if missing:
        print(f"❌ 契约文件缺失: {missing}")
        return 1
    a, b = sha(DOC), sha(CLASSPATH)
    if a != b:
        print("❌ UI 协议契约漂移：doc 真源与后端 classpath 拷贝不一致")
        print(f"   doc       {a}  {DOC}")
        print(f"   classpath {b}  {CLASSPATH}")
        print("   修复：以后者为准用前者覆盖（doc 是真源），再重跑本脚本")
        return 1
    print(f"✅ 契约一：doc ↔ 后端 classpath 一致（sha256 {a[:12]}…）")

    schema = json.loads(DOC.read_text(encoding="utf-8"))
    proto = set(schema["definitions"]["control"]["properties"]["props"]["properties"].keys())
    front = front_whitelist()
    if proto != front:
        print("❌ 契约二：前端 PROP_WHITELIST 与协议 props 白名单不一致")
        print(f"   协议有、前端缺: {sorted(proto - front)}")
        print(f"   前端有、协议无: {sorted(front - proto)}")
        print(f"   修复：同步 {FRONT_CONSTANTS} 的 PROP_WHITELIST（逐项对齐 schema.json），再重跑本脚本")
        return 1
    print(f"✅ 契约二：前端 lint 白名单 ↔ 协议 props 一致（{len(front)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
