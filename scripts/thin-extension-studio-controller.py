#!/usr/bin/env python3
from pathlib import Path

p = Path(
    "bone-engine/bone-extension-engine/bone-extension-studio/src/main/java/"
    "com/bone/engine/extension/studio/adapter/web/controller/ExtensionManagementController.java"
)
t = p.read_text()

# imports cleanup
t = t.replace(
    "import com.bone.engine.extension.studio.application.command.handler.ExtensionCommandHandler;\n", ""
)
t = t.replace(
    "import com.bone.engine.extension.studio.application.service.StudioAuditService;\n", ""
)
t = t.replace(
    "import com.bone.engine.extension.studio.application.service.StudioIdempotencyService;\n", ""
)
t = t.replace(
    "import com.bone.engine.extension.studio.application.service.StudioLroService;\n", ""
)
t = t.replace("import com.bone.engine.extension.studio.domain.repository.StudioAuditRepository;\n", "")
t = t.replace("import com.fasterxml.jackson.core.JsonProcessingException;\n", "")
t = t.replace("import com.fasterxml.jackson.databind.ObjectMapper;\n", "")

if "ExtensionStudioCommandHandler" not in t:
    t = t.replace(
        "import com.bone.engine.extension.studio.application.command.handler.ExtPointCommandHandler;\n",
        "import com.bone.engine.extension.studio.application.command.handler.ExtPointCommandHandler;\n"
        "import com.bone.engine.extension.studio.application.command.handler.ExtensionStudioCommandHandler;\n"
        "import com.bone.engine.extension.studio.application.query.handler.StudioAuditQueryHandler;\n"
        "import com.bone.engine.extension.studio.application.query.handler.StudioOperationQueryHandler;\n",
    )

# fields
t = t.replace(
    """    private final ExtPointQueryHandler extPointQueryHandler;
    private final ExtPointCommandHandler extPointCommandHandler;
    private final ExtensionQueryHandler extensionQueryHandler;
    private final ExtensionCommandHandler extensionCommandHandler;
    private final PluginExecutionLogQueryHandler pluginExecutionLogQueryHandler;
    private final PluginExecutionLogCommandHandler pluginExecutionLogCommandHandler;
    private final ExtensionStudioProperties studioProperties;
    private final StudioAuditService auditService;
    private final StudioAuditRepository auditRepository;
    private final StudioIdempotencyService idempotencyService;
    private final StudioLroService lroService;
    private final ObjectMapper objectMapper;
""",
    """    private final ExtPointQueryHandler extPointQueryHandler;
    private final ExtPointCommandHandler extPointCommandHandler;
    private final ExtensionQueryHandler extensionQueryHandler;
    private final ExtensionStudioCommandHandler extensionStudioCommandHandler;
    private final PluginExecutionLogQueryHandler pluginExecutionLogQueryHandler;
    private final PluginExecutionLogCommandHandler pluginExecutionLogCommandHandler;
    private final StudioOperationQueryHandler studioOperationQueryHandler;
    private final StudioAuditQueryHandler studioAuditQueryHandler;
    private final ExtensionStudioProperties studioProperties;
""",
)

blocks = {
    """    public ResponseEntity<ApiResponse<ExtPoint>> createPoint(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExtPoint body)
            throws JsonProcessingException {
        if (body == null || !hasText(body.getName()) || !hasText(body.getInterfaceName())) {
            return badRequest("扩展点名称与 interfaceName 不能为空");
        }
        String path = "/api/v1/extension/points";
        String fingerprint = StudioIdempotencyService.fingerprint(objectMapper.writeValueAsString(body));
        var replay = idempotencyService.<ExtPoint>replay(idempotencyKey, "POST", path, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        ExtPoint saved = extPointCommandHandler.saveExtPoint(body);
        auditService.success("ext_point.create", "ext_point", String.valueOf(saved.getId()));
        ResponseEntity<ApiResponse<ExtPoint>> response =
                StudioHttpSupport.created(StudioHttpSupport.resourceLocation("points", saved.getId()), "创建扩展点成功", saved);
        idempotencyService.remember(idempotencyKey, "POST", path, fingerprint, response);
        return response;
    }""": """    public ResponseEntity<ApiResponse<ExtPoint>> createPoint(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExtPoint body)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        return extPointCommandHandler.createPoint(idempotencyKey, body);
    }""",
    """    public ResponseEntity<ApiResponse<ExtPoint>> updatePoint(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ExtPoint body) {
        Integer expected = StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null);
        ExtPoint updated = extPointCommandHandler.updateExtPoint(id, body, expected);
        if (updated == null) {
            return notFound("扩展点不存在");
        }
        auditService.success("ext_point.update", "ext_point", String.valueOf(id));
        return StudioHttpSupport.ok("更新扩展点成功", updated);
    }""": """    public ResponseEntity<ApiResponse<ExtPoint>> updatePoint(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ExtPoint body) {
        return extPointCommandHandler.updatePoint(
                id, body, StudioHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    }""",
    """    public ResponseEntity<Void> deletePoint(@PathVariable Long id) {
        extPointCommandHandler.deleteExtPoint(id);
        auditService.success("ext_point.delete", "ext_point", String.valueOf(id));
        return ResponseEntity.noContent().build();
    }""": """    public ResponseEntity<Void> deletePoint(@PathVariable Long id) {
        return extPointCommandHandler.deletePoint(id);
    }""",
}

for old, new in blocks.items():
    if old in t:
        t = t.replace(old, new)
    else:
        print("WARN missing block:", old[:60])

# createPlugin
old_create = """    public ResponseEntity<ApiResponse<Extension>> createPlugin(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Extension body)
            throws JsonProcessingException {
        try {
            if (body == null || body.getExtPointId() == null || !hasText(body.getName()) || !hasText(body.getClassName())) {
                return badRequest("extPointId、name、className 不能为空");
            }
            String path = "/api/v1/extension/plugins";
            String fingerprint = StudioIdempotencyService.fingerprint(objectMapper.writeValueAsString(body));
            var replay = idempotencyService.<Extension>replay(idempotencyKey, "POST", path, fingerprint);
            if (replay.isPresent()) {
                return replay.get();
            }
            Extension saved = extensionCommandHandler.saveExtension(body);
            auditService.success("plugin.create", "plugin", String.valueOf(saved.getId()));
            ResponseEntity<ApiResponse<Extension>> response =
                    StudioHttpSupport.created(
                            StudioHttpSupport.resourceLocation("plugins", saved.getId()), "创建插件成功", saved);
            idempotencyService.remember(idempotencyKey, "POST", path, fingerprint, response);
            return response;
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }"""

new_create = """    public ResponseEntity<ApiResponse<Extension>> createPlugin(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Extension body)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        return extensionStudioCommandHandler.createPlugin(idempotencyKey, body);
    }"""

if old_create in t:
    t = t.replace(old_create, new_create)

p.write_text(t)
print("patched", p)
