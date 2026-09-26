package com.bone.engine.extension.studio.domain.gateway;

/**
 * 租户目录读侧端口（5a 姊妹篇 G2）：校验 tenantCode 是否为真实租户编码。
 *
 * <p>As-Is 以只读方式查询共享库 {@code iam_tenant}（EMBEDDED 部署同库）；目标态宜改走 IAM API， 届时仅需替换本端口实现。返回 {@code null}
 * 表示目录不可用（无法判定），调用方应放行并降级为格式校验。
 */
public interface TenantDirectoryPort {

  /**
   * @return true=编码存在；false=编码不存在；null=目录不可用
   */
  Boolean exists(String tenantCode);
}
