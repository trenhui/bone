package com.bone.procurement.sourcing.domain.gateway;

import com.bone.procurement.sourcing.domain.model.sourcing.Rfx;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import java.util.Optional;

/** 寻源仓储端口（只声明白名单方法：save / findByProjectNo / findRfxByNo）。 */
public interface SourcingProjectRepository {
  Long save(SourcingProject project);

  Long saveRfx(Rfx rfx);

  Optional<SourcingProject> findByProjectNo(Long tenantId, String projectNo);

  Optional<Rfx> findRfxByNo(Long tenantId, String rfxNo);

  /** 按租户 + RFx 编号定位对应寻源项目；RFx 不存在或 projectId 不命中返回 empty（租户锚定）。 */
  default Optional<SourcingProject> findProjectByRfxNo(Long tenantId, String rfxNo) {
    return Optional.empty();
  }
}
