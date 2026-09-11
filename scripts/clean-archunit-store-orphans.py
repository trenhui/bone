#!/usr/bin/env python3
"""清理 archunit_store 孤儿基线：删除已移除规则（2.5 四条降级 + 旧版 adapter 规则）的存档。

- 识别 stored.rules 中匹配孤儿描述的行，删除其 UUID 文件与条目
- 只清理本 change 移除/改造的规则，保留其它冻结基线
"""
import glob
import os
import re
import sys

# stored.rules 为 properties 格式：每个空格被转义为 "\\ "，故标记必须不含空格。
# 引号内容 / '@' / '.' / '/' 不转义，可作为锚点。
ORPHAN_MARKERS = [
    "'CommandHandler'",  # 2.5：commandHandlersShouldBeNamedCommandHandler
    "'QueryHandler'",  # 2.5：queryHandlersShouldBeNamedQueryHandler
    "handle/execute",  # 2.5：两条事务规则（描述含 "on class or handle/execute method"）
]

# 2.6 改造前旧版 adapter 规则：描述为 "...'..application.service..', because DDD P0-7..."
# 新版为 "...'..application.service..' or reside in a package '..application..'..."，据此区分
OLD_ADAPTER_MARKER = "'..application.service..',"


def main():
    roots = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]
    stores = []
    for root in roots:
        stores.extend(glob.glob(os.path.join(root, "**", "archunit_store"), recursive=True))
    stores = sorted(set(stores))
    if not stores:
        print("no archunit_store found")
        return

    total_orphan_files = 0
    for store in stores:
        rules_path = os.path.join(store, "stored.rules")
        if not os.path.exists(rules_path):
            continue
        with open(rules_path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        kept = []
        orphan_uuids = []
        for line in lines:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                kept.append(line)
                continue
            is_orphan = any(m in stripped for m in ORPHAN_MARKERS)
            if not is_orphan:
                is_orphan = OLD_ADAPTER_MARKER in stripped
            if is_orphan:
                m = re.search(r"=([0-9a-f-]{36})\s*$", stripped)
                if m:
                    orphan_uuids.append(m.group(1))
                continue
            kept.append(line)

        if orphan_uuids:
            with open(rules_path, "w", encoding="utf-8") as f:
                f.writelines(kept)
            for uuid in orphan_uuids:
                fpath = os.path.join(store, uuid)
                if os.path.exists(fpath):
                    os.remove(fpath)
                    total_orphan_files += 1
            print(f"cleaned {store}: removed {len(orphan_uuids)} orphan entries")

    print(f"total orphan files removed: {total_orphan_files}")


if __name__ == "__main__":
    sys.exit(main())
