package com.bone.engine.extension.studio.infrastructure.persistence;

import com.bone.engine.extension.studio.domain.gateway.TenantDirectoryPort;
import com.bone.engine.extension.studio.infrastructure.persistence.entity.ExtIamTenantDirectory;
import com.bone.engine.extension.studio.infrastructure.persistence.repository.ExtIamTenantDirectoryRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 租户目录 Metadata 实现：只读查询共享库 iam_tenant。
 *
 * <p>任何查询异常（如表不存在、库不可用）一律返回 null（目录不可用），由调用方降级为格式校验， 避免目录故障阻断插件登记主流程。
 */
@Repository
@ConditionalOnProperty(
    prefix = "bone.extension.studio.persistence",
    name = "mode",
    havingValue = "metadata")
public class MetadataTenantDirectoryAdapter implements TenantDirectoryPort {

  private final ExtIamTenantDirectoryRepository repository;

  public MetadataTenantDirectoryAdapter(ExtIamTenantDirectoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public Boolean exists(String tenantCode) {
    if (!StringUtils.hasText(tenantCode)) {
      return Boolean.FALSE;
    }
    try {
      Criteria<ExtIamTenantDirectory> criteria =
          Criteria.<ExtIamTenantDirectory>create().eq(ExtIamTenantDirectory::getCode, tenantCode);
      return !repository.findByCriteria(criteria).isEmpty();
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
