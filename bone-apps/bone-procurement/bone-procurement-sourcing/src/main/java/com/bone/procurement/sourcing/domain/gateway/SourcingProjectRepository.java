package com.bone.procurement.sourcing.domain.gateway;

import com.bone.procurement.sourcing.domain.model.Rfx;
import com.bone.procurement.sourcing.domain.model.SourcingProject;
import java.util.Optional;

/** 寻源仓储端口（只声明白名单方法：save / findByProjectNo / findRfxByNo）。 */
public interface SourcingProjectRepository {
  Long save(SourcingProject project);

  Long saveRfx(Rfx rfx);

  Optional<SourcingProject> findByProjectNo(Long tenantId, String projectNo);

  Optional<Rfx> findRfxByNo(Long tenantId, String rfxNo);
}
