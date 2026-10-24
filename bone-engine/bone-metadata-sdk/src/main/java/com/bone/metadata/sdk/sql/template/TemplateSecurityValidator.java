package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.exception.TemplateSecurityException;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TemplateSecurityValidator {
  private final SqlConfigProperties config;

  /**
   * 模板文本的注入特征模式。
   *
   * <p><b>为什么不禁 {@code DELETE}</b>：DELETE 是合法 DML——仓储层「原子释放槽位」等语义必须物理删行（软删不释放
   * 主键，会把「终态后可重提」永久焊死）。而禁它并无安全收益：① executor 层的 {@code SqlSecurityGuard} 对 INSERT/UPDATE/DELETE
   * <b>刻意跳过</b>关键字扫描（写操作由 SDK 构建），仅对其余语句禁 DDL 关键字与 {@code ;} 叠语句； ② {@code ${}} 原文替换的取值已被 {@code
   * MyBatisSqlProcessor} 约束为 {@code ^[a-zA-Z0-9_]+$}（标识符字符集）， 关键字载荷活不过替换。禁裸词 {@code delete}
   * 只会把「任何需要物理删除的 SDK 仓储」一棍子打死。
   *
   * <p>保留 DDL 关键字（drop/truncate）与 {@code exec} / {@code union all}：数据访问模板出现 DDL 与堆叠查询即越界，
   * 属防御性纵深（真注入面已由上述两层闭合）。
   */
  private static final Pattern SQL_INJECTION_PATTERN =
      Pattern.compile("(?i)(\\b(drop|truncate|exec|union\\s+all)\\b)");

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
      Set<String> allowedHosts = config.getSecurity().getAllowedHosts();
      String host = uri.getHost();

      if (!isHostAllowed(host, allowedHosts)) {
        throw new TemplateSecurityException("Disallowed host: " + host);
      }
    }
  }

  /** 检查主机是否在允许的主机列表中 支持通配符匹配，如 "*.bone.com" */
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
