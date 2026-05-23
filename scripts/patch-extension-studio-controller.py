#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STUDIO = ROOT / "bone-engine/bone-extension-engine/bone-extension-studio"

ctrl = STUDIO / "src/main/java/com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java"
t = ctrl.read_text()

replacements = [
    ("import com.bone.engine.extension.studio.service.ExtPointService;\n", ""),
    ("import com.bone.engine.extension.studio.service.ExtensionService;\n", ""),
    ("import com.bone.engine.extension.studio.service.PluginExecutionLogService;\n", ""),
    (
        "import com.bone.engine.extension.studio.service.StudioAuditService;\n",
        "import com.bone.engine.extension.studio.application.query.handler.ExtPointQueryHandler;\n"
        "import com.bone.engine.extension.studio.application.command.handler.ExtPointCommandHandler;\n"
        "import com.bone.engine.extension.studio.application.query.handler.ExtensionQueryHandler;\n"
        "import com.bone.engine.extension.studio.application.command.handler.ExtensionCommandHandler;\n"
        "import com.bone.engine.extension.studio.application.query.handler.PluginExecutionLogQueryHandler;\n"
        "import com.bone.engine.extension.studio.application.command.handler.PluginExecutionLogCommandHandler;\n"
        "import com.bone.engine.extension.studio.application.service.StudioAuditService;\n",
    ),
    (
        "import com.bone.engine.extension.studio.service.StudioIdempotencyService;\n",
        "import com.bone.engine.extension.studio.application.service.StudioIdempotencyService;\n",
    ),
    (
        "import com.bone.engine.extension.studio.service.StudioLroService;\n",
        "import com.bone.engine.extension.studio.application.service.StudioLroService;\n",
    ),
    (
        "private final ExtPointService extPointService;\n",
        "private final ExtPointQueryHandler extPointQueryHandler;\n"
        "    private final ExtPointCommandHandler extPointCommandHandler;\n",
    ),
    (
        "private final ExtensionService extensionService;\n",
        "private final ExtensionQueryHandler extensionQueryHandler;\n"
        "    private final ExtensionCommandHandler extensionCommandHandler;\n",
    ),
    (
        "private final PluginExecutionLogService executionLogService;\n",
        "private final PluginExecutionLogQueryHandler pluginExecutionLogQueryHandler;\n"
        "    private final PluginExecutionLogCommandHandler pluginExecutionLogCommandHandler;\n",
    ),
]
for old, new in replacements:
    t = t.replace(old, new)

for m in [
    "searchExtPoints",
    "findExtPointsByDomain",
    "findExtPointsByCategory",
    "findAllExtPoints",
    "findExtPointById",
]:
    t = t.replace(f"extPointService.{m}", f"extPointQueryHandler.{m}")
for m in ["saveExtPoint", "updateExtPoint", "deleteExtPoint", "enableExtPoint"]:
    t = t.replace(f"extPointService.{m}", f"extPointCommandHandler.{m}")

for m in [
    "searchExtensions",
    "findExtensionsByExtPointId",
    "findExtensionsByTenantCode",
    "findAllExtensions",
    "findExtensionById",
    "listPluginVersions",
]:
    t = t.replace(f"extensionService.{m}", f"extensionQueryHandler.{m}")
for m in [
    "saveExtension",
    "updateExtension",
    "deleteExtension",
    "uploadPluginArtifact",
    "rollbackExtension",
    "publishRuntime",
    "undeployExtension",
    "deployExtension",
    "simulatePluginExecution",
]:
    t = t.replace(f"extensionService.{m}", f"extensionCommandHandler.{m}")

t = t.replace("executionLogService.overview", "pluginExecutionLogQueryHandler.overview")
t = t.replace("executionLogService.queryByCursor", "pluginExecutionLogQueryHandler.queryByCursor")
t = t.replace("executionLogService.query", "pluginExecutionLogQueryHandler.query")
t = t.replace("executionLogService.count", "pluginExecutionLogQueryHandler.count")
t = t.replace(
    "executionLogService\n                .ingestFromRuntime",
    "pluginExecutionLogCommandHandler\n                .ingestFromRuntime",
)
ctrl.write_text(t)

di = STUDIO / "src/main/java/com/bone/engine/extension/studio/config/DataInitializer.java"
d = di.read_text()
d = d.replace(
    "import com.bone.engine.extension.studio.service.PluginExecutionLogService;",
    "import com.bone.engine.extension.studio.application.command.handler.PluginExecutionLogCommandHandler;",
)
d = d.replace(
    "PluginExecutionLogService executionLogService",
    "PluginExecutionLogCommandHandler executionLogCommandHandler",
)
d = d.replace("executionLogService.record", "executionLogCommandHandler.record")
di.write_text(d)
print("patched controller and DataInitializer")
