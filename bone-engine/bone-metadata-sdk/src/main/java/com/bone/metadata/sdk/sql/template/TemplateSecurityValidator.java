package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.exception.TemplateSecurityException;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;


import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TemplateSecurityValidator {
    private final SqlConfigProperties config;
    
    public TemplateSecurityValidator(SqlConfigProperties config) {
        this.config = config;
    }
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("(?i)(\\b(drop|delete|truncate|exec|union\\s+all)\\b)");

    // 不再使用硬编码的 ALLOWED_HOSTS

    public void validateDescriptor(TemplateDescriptor descriptor) {
        if (descriptor == null) {
            throw new TemplateSecurityException("TemplateDescriptor cannot be null");
        }
        validateSourceUri(URI.create(descriptor.getSourceUri()));
        validateTenantAccess(descriptor.getTags());
    }

    public void validateContent(String content, TemplateDescriptor descriptor) {
        if (content.length() > config.getTemplate().getMaxTemplateSize()) {
            throw new TemplateSecurityException("Template size exceeds limit");
        }
        if (SQL_INJECTION_PATTERN.matcher(content).find()) {
            throw new TemplateSecurityException("Potential SQL injection detected");
        }
    }

    private void validateSourceUri(URI uri) {
        if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
            List<String> allowedHostsList = config.getSecurity().getAllowedHosts();
        Set<String> allowedHosts = new HashSet<>(allowedHostsList);
            String host = uri.getHost();

            if (!isHostAllowed(host, allowedHosts)) {
                throw new TemplateSecurityException("Disallowed host: " + host);
            }
        }
    }

    /**
     * 检查主机是否在允许的主机列表中
     * 支持通配符匹配，如 "*.bone.com"
     */
    private boolean isHostAllowed(String host, Set<String> allowedHosts) {
        if (host == null || allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }

        // 精确匹配
        if (allowedHosts.contains(host)) {
            return true;
        }

        // 通配符匹配
        for (String pattern : allowedHosts) {
            if (pattern.startsWith("*.")) {
                String domain = pattern.substring(2);
                if (host.endsWith(domain) && host.length() > domain.length()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void validateTenantAccess(Map<String, String> tags) {
        if (config.getTenant().isEnabled()) {
            String tenantId = tags.get("tenant");
            if (tenantId == null || !isValidTenant(tenantId)) {
                throw new TemplateSecurityException("Invalid or missing tenant ID");
            }
        }
    }

    private boolean isValidTenant(String tenantId) {
        // Placeholder for tenant validation logic
        // 可以根据实际需求实现租户验证逻辑
        return tenantId != null && !tenantId.trim().isEmpty();
    }
}