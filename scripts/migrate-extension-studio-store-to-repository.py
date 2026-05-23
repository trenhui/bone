#!/usr/bin/env python3
"""Migrate bone-extension-studio domain.store -> domain.repository."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STUDIO = ROOT / "bone-engine/bone-extension-engine/bone-extension-studio"

REPLACEMENTS = [
    ("com.bone.engine.extension.studio.domain.store.ExtensionStore", "com.bone.engine.extension.studio.domain.repository.ExtensionRepository"),
    ("com.bone.engine.extension.studio.domain.store.ExtPointStore", "com.bone.engine.extension.studio.domain.repository.ExtPointRepository"),
    ("com.bone.engine.extension.studio.domain.store.PluginVersionStore", "com.bone.engine.extension.studio.domain.repository.PluginVersionRepository"),
    ("com.bone.engine.extension.studio.domain.store.PluginExecutionLogStore", "com.bone.engine.extension.studio.domain.repository.PluginExecutionLogRepository"),
    ("com.bone.engine.extension.studio.domain.store.StudioAuditStore", "com.bone.engine.extension.studio.domain.repository.StudioAuditRepository"),
    ("ExtensionStore", "ExtensionRepository"),
    ("ExtPointStore", "ExtPointRepository"),
    ("PluginVersionStore", "PluginVersionRepository"),
    ("PluginExecutionLogStore", "PluginExecutionLogRepository"),
    ("StudioAuditStore", "StudioAuditRepository"),
    ("extensionStore", "extensionRepository"),
    ("extPointStore", "extPointRepository"),
    ("pluginVersionStore", "pluginVersionRepository"),
    ("logStore", "logRepository"),
    ("auditStore", "auditRepository"),
]

CLASS_RENAMES = {
    "MetadataExtensionStore": "MetadataExtensionRepository",
    "InMemoryExtensionStore": "InMemoryExtensionRepository",
    "MetadataExtPointStore": "MetadataExtPointRepository",
    "InMemoryExtPointStore": "InMemoryExtPointRepository",
    "MetadataPluginVersionStore": "MetadataPluginVersionRepository",
    "InMemoryPluginVersionStore": "InMemoryPluginVersionRepository",
    "MetadataPluginExecutionLogStore": "MetadataPluginExecutionLogRepository",
    "InMemoryPluginExecutionLogStore": "InMemoryPluginExecutionLogRepository",
    "MetadataStudioAuditStore": "MetadataStudioAuditRepository",
    "InMemoryStudioAuditStore": "InMemoryStudioAuditRepository",
}


def migrate_java_files() -> int:
    changed = 0
    for path in STUDIO.rglob("*.java"):
        if "domain/store" in str(path):
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        for old, new in REPLACEMENTS:
            text = text.replace(old, new)
        for old, new in CLASS_RENAMES.items():
            text = text.replace(old, new)
        if "Extension / ExtPointStore" in text:
            text = text.replace("Extension / ExtPointStore", "Extension / ExtPointRepository")
        if text != original:
            path.write_text(text, encoding="utf-8")
            changed += 1
            print(f"updated {path.relative_to(ROOT)}")
    return changed


def rename_impl_files() -> None:
    persist = STUDIO / "src/main/java/com/bone/engine/extension/studio/infrastructure/persistence"
    test_persist = STUDIO / "src/test/java/com/bone/engine/extension/studio/infrastructure/persistence"
    for base in (persist, test_persist):
        if not base.exists():
            continue
        for path in list(base.glob("*Store*.java")):
            new_name = path.name
            for old, new in CLASS_RENAMES.items():
                new_name = new_name.replace(old, new)
            target = path.with_name(new_name)
            if target != path:
                path.rename(target)
                print(f"renamed {path.name} -> {target.name}")


def delete_store_package() -> None:
    store_dir = STUDIO / "src/main/java/com/bone/engine/extension/studio/domain/store"
    if store_dir.exists():
        for f in store_dir.glob("*.java"):
            f.unlink()
            print(f"deleted {f.relative_to(ROOT)}")
        try:
            store_dir.rmdir()
        except OSError:
            pass
    dead = STUDIO / "src/main/java/com/bone/engine/extension/studio/domain/repository/ExtensionEntityRepository.java"
    if dead.exists():
        dead.unlink()
        print(f"deleted {dead.relative_to(ROOT)}")


def main() -> None:
    delete_store_package()
    rename_impl_files()
    n = migrate_java_files()
    print(f"done, {n} files updated")


if __name__ == "__main__":
    main()
