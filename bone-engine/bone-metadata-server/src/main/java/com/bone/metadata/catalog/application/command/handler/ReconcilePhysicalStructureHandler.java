package com.bone.metadata.catalog.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物理结构治理用例：删除单个孤儿物理列 / 批量清理孤儿列。
 *
 * <p>破坏性操作，仅由管理员通过专属端点显式触发。仅 RUNTIME 实体拥有可被治理的物理表；非 RUNTIME 实体（保留列/扩展列模式）直接拒绝。
 */
@Component
@RequiredArgsConstructor
public class ReconcilePhysicalStructureHandler {

  private final MetaEntityRepository metaEntityRepository;
  private final PhysicalStructureGateway physicalStructureGateway;

  @Transactional
  public PhysicalStructurePlan dropColumn(Long entityId, String fieldCode) {
    MetaEntity entity = requireRuntimeEntity(entityId);
    return physicalStructureGateway.dropColumn(entity.getTenantId(), entity.getCode(), fieldCode);
  }

  @Transactional
  public PhysicalStructurePlan dropDriftedColumns(Long entityId) {
    MetaEntity entity = requireRuntimeEntity(entityId);
    return physicalStructureGateway.dropDriftedColumns(entity.getTenantId(), entity.getCode());
  }

  private MetaEntity requireRuntimeEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw BizException.of("实体不存在: " + entityId);
    }
    if (!MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      throw BizException.of("仅 RUNTIME 实体支持物理结构维护: " + entity.getCode());
    }
    return entity;
  }
}
