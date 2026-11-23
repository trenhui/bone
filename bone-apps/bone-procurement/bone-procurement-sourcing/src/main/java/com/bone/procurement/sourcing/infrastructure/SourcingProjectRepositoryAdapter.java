package com.bone.procurement.sourcing.infrastructure;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.sourcing.Rfx;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import com.bone.procurement.sourcing.domain.repository.RfxSqlRepository;
import com.bone.procurement.sourcing.domain.repository.SourcingProjectSqlRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 寻源仓储适配器：实现 domain/gateway 端口，把方法委托给 SDK 仓储（application 层不直调 SDK）。 */
@Component
public class SourcingProjectRepositoryAdapter implements SourcingProjectRepository {

  private final SourcingProjectSqlRepository projects;
  private final RfxSqlRepository rfxes;

  public SourcingProjectRepositoryAdapter(
      SourcingProjectSqlRepository projects, RfxSqlRepository rfxes) {
    this.projects = projects;
    this.rfxes = rfxes;
  }

  @Override
  public Long save(SourcingProject project) {
    return projects.save(project);
  }

  @Override
  public Long saveRfx(Rfx rfx) {
    return rfxes.save(rfx);
  }

  @Override
  public Optional<SourcingProject> findByProjectNo(Long tenantId, String projectNo) {
    return projects.findByProjectNo(tenantId, projectNo);
  }

  @Override
  public Optional<Rfx> findRfxByNo(Long tenantId, String rfxNo) {
    return rfxes.findByRfxNo(tenantId, rfxNo);
  }

  @Override
  public Optional<SourcingProject> findProjectByRfxNo(Long tenantId, String rfxNo) {
    if (tenantId == null || rfxNo == null || rfxNo.isBlank()) {
      return Optional.empty();
    }
    return rfxes
        .findByRfxNo(tenantId, rfxNo)
        .map(Rfx::getProjectId)
        .flatMap(projectId -> projects.findById(tenantId, projectId));
  }
}
