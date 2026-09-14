package com.bone.metadata.catalog.application.query;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 元数据唯一性查询（应用读侧）。
 *
 * <p>按 DDD P0-6 规范，读侧 DSL（{@code where/count}）仅允许在应用读侧使用； command handler 与 domain
 * 层禁止直接依赖读侧能力，故唯一性判定由此读侧服务承载， 由写用例调用本服务获取结果，而非自行查询。
 */
@Component
@RequiredArgsConstructor
public class MetaEntityUniquenessQuery {

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;

  public boolean entityCodeExists(long tenantId, String code) {
    return metaEntityRepository
            .where(MetaEntity::getTenantId)
            .eq(tenantId)
            .and(MetaEntity::getCode)
            .eq(code)
            .count()
        > 0;
  }

  public boolean fieldCodeExists(long entityId, String code) {
    return metaFieldRepository
            .where(MetaField::getEntityId)
            .eq(entityId)
            .and(MetaField::getCode)
            .eq(code)
            .count()
        > 0;
  }

  public void assertEntityCodeUnique(long tenantId, String code) {
    if (entityCodeExists(tenantId, code)) {
      throw BizException.of("实体编码已存在: " + code);
    }
  }

  public void assertFieldCodeUnique(long entityId, String code) {
    if (fieldCodeExists(entityId, code)) {
      throw BizException.of("字段编码已存在: " + code);
    }
  }
}
