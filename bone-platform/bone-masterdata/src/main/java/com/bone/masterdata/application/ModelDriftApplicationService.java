package com.bone.masterdata.application;

import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import com.bone.masterdata.domain.model.drift.ModelDrift;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.MasterDataField;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.ModelDriftRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型漂移对账应用服务（G16，UC-T10）：主数据字段 vs 来源元数据字段结构比对。
 *
 * <p>设计红线（§UC-T10）：检测只产出漂移记录，<b>禁止自动同步</b>——破坏性变更（字段删除/类型变化）须人工处置。
 */
@Service
@RequiredArgsConstructor
public class ModelDriftApplicationService {

  private final ModelDriftRepository driftRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;
  private final MetaEntityCatalogPort metaEntityCatalogPort;
  private final CurrentUserPort currentUserPort;

  /**
   * 对账（UC-T10）：对有来源 meta_entity 的主数据实体，比对字段集，产出 FIELD_ADDED / FIELD_REMOVED / TYPE_CHANGED
   * 漂移记录（幂等：同类漂移已存在且未处置则不重复写）。
   */
  @Transactional
  public List<ModelDrift> reconcile(Long masterDataEntityId) {
    MasterDataEntity entity = entityRepository.findById(masterDataEntityId);
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    if (entity.getMetaEntityId() == null) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.MODEL_DRIFT_NO_SOURCE, "该实体为租户自建（无来源元数据），无需对账");
    }
    List<MasterDataField> mdmFields = fieldRepository.findByMasterDataEntityId(entity.getId());
    Map<String, MasterDataField> mdmByCode = new HashMap<>();
    for (MasterDataField field : mdmFields) {
      mdmByCode.put(field.getCode().value(), field);
    }
    Map<String, MetaEntityCatalogPort.MetaFieldRow> metaByCode = new HashMap<>();
    for (MetaEntityCatalogPort.MetaFieldRow row :
        metaEntityCatalogPort.requireFields(entity.getMetaEntityId())) {
      metaByCode.put(row.code(), row);
    }
    int detected = 0;
    // 元数据新增 / 类型变化
    for (MetaEntityCatalogPort.MetaFieldRow meta : metaByCode.values()) {
      MasterDataField local = mdmByCode.get(meta.code());
      if (local == null) {
        detected += saveIfAbsent(entity, "FIELD_ADDED", meta.code(), null, meta.type(), false);
      } else if (local.getType() != null && !local.getType().equalsIgnoreCase(meta.type())) {
        detected +=
            saveIfAbsent(entity, "TYPE_CHANGED", meta.code(), local.getType(), meta.type(), true);
      }
    }
    // 主数据侧字段在元数据中已消失（破坏性）
    for (String code : mdmByCode.keySet()) {
      if (!metaByCode.containsKey(code)) {
        detected +=
            saveIfAbsent(entity, "FIELD_REMOVED", code, mdmByCode.get(code).getType(), null, true);
      }
    }
    return detected > 0 ? driftRepository.findByEntityId(masterDataEntityId) : List.of();
  }

  private int saveIfAbsent(
      MasterDataEntity entity,
      String driftType,
      String fieldCode,
      String oldValue,
      String newValue,
      boolean destructive) {
    if (driftRepository.countByEntityFieldAndType(entity.getId(), fieldCode, driftType) > 0) {
      return 0;
    }
    driftRepository.insert(
        ModelDrift.detected(
            DistributedIdGenerator.generateLongId(),
            entity.getId(),
            entity.getMetaEntityId(),
            driftType,
            fieldCode,
            oldValue,
            newValue,
            destructive));
    return 1;
  }

  @Transactional
  public void handle(Long id, String targetStatus) {
    ModelDrift drift = driftRepository.findById(id);
    if (drift == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.MODEL_DRIFT_NOT_FOUND, "漂移记录不存在: " + id);
    }
    drift.handle(targetStatus, currentUserPort.requireUserId());
    driftRepository.update(drift);
  }

  @Transactional(readOnly = true)
  public List<ModelDrift> byEntity(Long masterDataEntityId) {
    return driftRepository.findByEntityId(masterDataEntityId);
  }
}
