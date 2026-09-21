#!/usr/bin/env python3
"""一次性收敛脚本：bone-iam 出站端口 / gateway 命名（E-13.3 + E-10.2）。

只做三件事：
1. 文本替换（FQCN → FQCN、路径 → 路径、简名 → 简名）
2. 文件迁移（含 package 行改写）
3. 删除迁空后的旧文件

不做任何语义改动。
"""
import os
import re
import shutil

ROOT = "/Users/renhui.trh/wps/bone"
IAM_MAIN = os.path.join(ROOT, "bone-platform/bone-iam/src/main/java/com/bone/iam")
IAM_TEST = os.path.join(ROOT, "bone-platform/bone-iam/src/test/java/com/bone/iam")

GW = "com.bone.iam.infrastructure.gateway"

# (旧 FQCN, 新 FQCN)
FQCN = [
    ("com.bone.iam.infrastructure.security.IamJwtTokenService",
     GW + ".AccessTokenIssuerGatewayAdapter"),
    ("com.bone.iam.infrastructure.security.AuthorityCacheEvictionService",
     GW + ".AccountAuthorityCacheGatewayAdapter"),
    ("com.bone.iam.infrastructure.security.RefreshTokenService",
     GW + ".RefreshTokenIssuerGatewayAdapter"),
    ("com.bone.iam.infrastructure.persistence.AuditSettingsStoreImpl",
     GW + ".AuditSettingsGatewayAdapter"),
    ("com.bone.iam.infrastructure.persistence.RefreshTokenSessionStoreImpl",
     GW + ".RefreshTokenSessionGatewayAdapter"),
    ("com.bone.iam.infrastructure.tenant.TenantProviderAdapter",
     GW + ".TenantProviderGatewayAdapter"),
    ("com.bone.iam.domain.gateway.AuditSettingsStore",
     "com.bone.iam.domain.gateway.AuditSettingsGateway"),
    ("com.bone.iam.domain.gateway.RefreshTokenSessionStore",
     "com.bone.iam.domain.gateway.RefreshTokenSessionGateway"),
    ("com.bone.iam.domain.gateway.PasswordEncoderPort",
     "com.bone.iam.application.port.out.PasswordEncoderPort"),
]

# javadoc / 注释里的路径引用（写在 FQCN 之后、简名之前处理）
PATHS = [
    ("infrastructure/persistence/RefreshTokenSessionStoreImpl",
     "infrastructure/gateway/RefreshTokenSessionGatewayAdapter"),
    ("infrastructure/security/IamJwtTokenService",
     "infrastructure/gateway/AccessTokenIssuerGatewayAdapter"),
    ("infrastructure/tenant/TenantProviderAdapter",
     "infrastructure/gateway/TenantProviderGatewayAdapter"),
    ("infrastructure/security/SpringPasswordEncoderAdapter",
     "infrastructure/security/PasswordEncoderPortAdapter"),
]

# (旧简名, 新简名)
SIMPLE = [
    ("IamJwtTokenService", "AccessTokenIssuerGatewayAdapter"),
    ("AuthorityCacheEvictionService", "AccountAuthorityCacheGatewayAdapter"),
    ("RefreshTokenService", "RefreshTokenIssuerGatewayAdapter"),
    ("AuditSettingsStoreImpl", "AuditSettingsGatewayAdapter"),
    ("RefreshTokenSessionStoreImpl", "RefreshTokenSessionGatewayAdapter"),
    ("SpringPasswordEncoderAdapter", "PasswordEncoderPortAdapter"),
    ("TenantProviderAdapter", "TenantProviderGatewayAdapter"),
    ("AuditSettingsStore", "AuditSettingsGateway"),
    ("RefreshTokenSessionStore", "RefreshTokenSessionGateway"),
]

# (相对 IAM_MAIN 的旧路径, 新路径, 新包名)
MOVES = [
    ("infrastructure/security/IamJwtTokenService.java",
     "infrastructure/gateway/AccessTokenIssuerGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/security/AuthorityCacheEvictionService.java",
     "infrastructure/gateway/AccountAuthorityCacheGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/security/RefreshTokenService.java",
     "infrastructure/gateway/RefreshTokenIssuerGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/persistence/AuditSettingsStoreImpl.java",
     "infrastructure/gateway/AuditSettingsGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/persistence/RefreshTokenSessionStoreImpl.java",
     "infrastructure/gateway/RefreshTokenSessionGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/tenant/TenantProviderAdapter.java",
     "infrastructure/gateway/TenantProviderGatewayAdapter.java",
     "com.bone.iam.infrastructure.gateway"),
    ("infrastructure/security/SpringPasswordEncoderAdapter.java",
     "infrastructure/security/PasswordEncoderPortAdapter.java",
     "com.bone.iam.infrastructure.security"),
    ("domain/gateway/AuditSettingsStore.java",
     "domain/gateway/AuditSettingsGateway.java",
     "com.bone.iam.domain.gateway"),
    ("domain/gateway/RefreshTokenSessionStore.java",
     "domain/gateway/RefreshTokenSessionGateway.java",
     "com.bone.iam.domain.gateway"),
    ("domain/gateway/PasswordEncoderPort.java",
     "../application/port/out/PasswordEncoderPort.java",
     "com.bone.iam.application.port.out"),
]

SKIP_DIRS = {"node_modules", "target", ".git", "dist", "build"}


def iter_java():
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for f in files:
            if f.endswith((".java", ".md", ".sql", ".yaml", ".yml")):
                yield os.path.join(base, f)


def main():
    changed = []
    for path in iter_java():
        try:
            with open(path, "r", encoding="utf-8") as fh:
                src = fh.read()
        except (UnicodeDecodeError, OSError):
            continue
        if "com.bone.iam" not in src:
            continue
        out = src
        for old, new in FQCN + PATHS:
            out = out.replace(old, new)
        for old, new in SIMPLE:
            out = re.sub(r"\b%s\b" % re.escape(old), new, out)
        if out != src:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(out)
            changed.append(os.path.relpath(path, ROOT))

    print("== 文本替换 %d 个文件 ==" % len(changed))
    for c in changed:
        print("  ", c)

    print("== 文件迁移 ==")
    for rel_old, rel_new, pkg in MOVES:
        old = os.path.normpath(os.path.join(IAM_MAIN, rel_old))
        new = os.path.normpath(os.path.join(IAM_MAIN, rel_new))
        if not os.path.exists(old):
            print("   MISS", rel_old)
            continue
        os.makedirs(os.path.dirname(new), exist_ok=True)
        with open(old, "r", encoding="utf-8") as fh:
            content = fh.read()
        content = re.sub(r"^package\s+[\w.]+;", "package %s;" % pkg, content, count=1,
                         flags=re.M)
        with open(new, "w", encoding="utf-8") as fh:
            fh.write(content)
        os.remove(old)
        print("   %s -> %s" % (rel_old, rel_new))

    # 清理空目录
    for d in ("infrastructure/tenant", "infrastructure/persistence"):
        p = os.path.join(IAM_MAIN, d)
        if os.path.isdir(p) and not os.listdir(p):
            os.rmdir(p)
            print("   rmdir", d)


if __name__ == "__main__":
    main()
