#!/usr/bin/env python3
"""Migrate bone-extension-studio service/ to application/command|query + application/service."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = (
    ROOT
    / "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/com/bone/engine/extension/studio"
)
TEST_BASE = (
    ROOT
    / "bone-engine/bone-extension-engine/bone-extension-studio/src/test/java/com/bone/engine/extension/studio"
)

OLD_SVC = "com.bone.engine.extension.studio.service"
APP_SVC = "com.bone.engine.extension.studio.application.service"
OLD_SVC_COMMON = f"{OLD_SVC}.common"
APP_SVC_COMMON = f"{APP_SVC}.common"
OLD_EXC = OLD_SVC
COMMON_EXC = "com.bone.engine.extension.studio.common.exception"

SHARED_SERVICES = [
    "StudioAuditService.java",
    "StudioIdempotencyService.java",
    "StudioLroService.java",
    "PluginArtifactService.java",
    "StudioVersionSupport.java",
]
EXCEPTIONS = ["IdempotencyConflictException.java", "OptimisticLockException.java"]
COMMON_UTILS = ["ClassScanner.java", "ResourceUtils.java"]


def replace_pkg(text: str, mapping: dict[str, str]) -> str:
    for old, new in mapping.items():
        text = text.replace(old, new)
    return text


def move_file(src: Path, dst: Path, new_package: str) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    text = src.read_text(encoding="utf-8")
    text = re.sub(r"^package\s+[\w.]+;", f"package {new_package};", text, count=1, flags=re.M)
    dst.write_text(text, encoding="utf-8")
    if src.exists():
        src.unlink()


def patch_java_tree(root: Path, mapping: dict[str, str]) -> None:
    for p in root.rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        new = replace_pkg(text, mapping)
        if new != text:
            p.write_text(new, encoding="utf-8")


def main() -> None:
    svc = BASE / "service"
    app_svc = BASE / "application/service"
    app_svc_common = app_svc / "common"
    common_exc = BASE / "common/exception"

    for name in SHARED_SERVICES:
        move_file(svc / name, app_svc / name, APP_SVC)
    for name in EXCEPTIONS:
        move_file(svc / name, common_exc / name, COMMON_EXC)
    for name in COMMON_UTILS:
        move_file(svc / "common" / name, app_svc_common / name, APP_SVC_COMMON)

    mapping = {
        OLD_SVC: APP_SVC,
        OLD_SVC_COMMON: APP_SVC_COMMON,
        f"{OLD_SVC}.impl": "com.bone.engine.extension.studio.application.command.handler",
        f"{OLD_EXC}.IdempotencyConflictException": f"{COMMON_EXC}.IdempotencyConflictException",
        f"{OLD_EXC}.OptimisticLockException": f"{COMMON_EXC}.OptimisticLockException",
    }
    patch_java_tree(BASE, mapping)
    patch_java_tree(TEST_BASE, mapping)

    # StudioLroService: ExtensionService -> ExtensionCommandHandler
    lro = app_svc / "StudioLroService.java"
    if lro.exists():
        t = lro.read_text(encoding="utf-8")
        t = t.replace("ExtensionService extensionService", "ExtensionCommandHandler extensionCommandHandler")
        t = t.replace("ExtensionService extensionService,", "ExtensionCommandHandler extensionCommandHandler,")
        t = t.replace("this.extensionService = extensionService", "this.extensionCommandHandler = extensionCommandHandler")
        t = t.replace("extensionService.deployExtension", "extensionCommandHandler.deployExtension")
        t = t.replace("extensionService.findExtensionById", "extensionCommandHandler.findExtensionById")
        t = t.replace(
            "import com.bone.engine.extension.studio.service.ExtensionService;",
            "import com.bone.engine.extension.studio.application.command.handler.ExtensionCommandHandler;",
        )
        lro.write_text(t, encoding="utf-8")

    # Remove obsolete interfaces and impl package
    for p in [svc / "ExtPointService.java", svc / "ExtensionService.java", svc / "PluginExecutionLogService.java"]:
        if p.exists():
            p.unlink()
    impl = svc / "impl"
    if impl.exists():
        shutil.rmtree(impl)
    if svc.exists() and not any(svc.iterdir()):
        svc.rmdir()
    common_dir = BASE / "service/common"
    if common_dir.exists() and not any(common_dir.iterdir()):
        common_dir.rmdir()

    print("Moved shared services and patched imports. Create handlers next (run generate-handlers).")


if __name__ == "__main__":
    main()
