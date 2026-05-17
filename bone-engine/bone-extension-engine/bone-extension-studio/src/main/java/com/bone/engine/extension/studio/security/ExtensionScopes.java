package com.bone.engine.extension.studio.security;

/** OpenAPI Scope，与 Bone-API-规范 §9.2 一致。 */
public final class ExtensionScopes {

    public static final String POINTS_READ = "extension:points:read";
    public static final String POINTS_WRITE = "extension:points:write";
    public static final String PLUGINS_DEPLOY = "extension:plugins:deploy";

    private ExtensionScopes() {}
}
