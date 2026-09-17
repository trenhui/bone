package com.bone.masterdata.infrastructure.query;

import com.bone.masterdata.application.query.port.MasterDataQueryPort;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.repository.DataStandardRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.standard.DataStandard;
import com.bone.masterdata.domain.standard.vo.StandardFieldCode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 主数据读侧适配器：{@link MasterDataQueryPort} 的基础设施实现（E-10.3 {@code infrastructure/query}）。
 *
 * <p>读侧 DSL（Criteria）仅允许出现在本实现中；应用层与领域层通过端口调用，不直接依赖持久化 DSL。
 */
@Component
@RequiredArgsConstructor
public class MasterDataQueryAdapter implements MasterDataQueryPort {

  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;
  private final DataStandardRepository dataStandardRepository;

  @Override
  public long countEntityByName(MasterDataEntityName name) {
    return entityRepository.countByCriteria(
        Criteria.<MasterDataEntity>create().entityClass(MasterDataEntity.class).eq("name", name));
  }

  @Override
  public Optional<Long> findEntityIdByMetaEntityId(Long metaEntityId) {
    List<MasterDataEntity> list =
        entityRepository.findByCriteria(
            Criteria.<MasterDataEntity>create()
                .entityClass(MasterDataEntity.class)
                .eq("metaEntityId", metaEntityId));
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0).getId());
  }

  @Override
  public long countFieldByEntityIdAndName(Long masterDataEntityId, FieldName name) {
    return fieldRepository.countByCriteria(
        Criteria.<MasterDataField>create()
            .entityClass(MasterDataField.class)
            .eq("masterDataEntityId", masterDataEntityId)
            .eq("name", name));
  }

  @Override
  public long countStandardByEntityCodeAndFieldCode(
      String entityCode, StandardFieldCode fieldCode) {
    return dataStandardRepository.countByCriteria(
        Criteria.<DataStandard>create()
            .entityClass(DataStandard.class)
            .eq("entityCode", entityCode)
            .eq("fieldCode", fieldCode));
  }
}
