package com.bone.procurement.sourcing.infrastructure;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.Rfx;
import com.bone.procurement.sourcing.domain.model.SourcingProject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/** 受控内存仓储：walking skeleton 跑通生命周期；生产持久化（t_proc_* 已落 DDL）后续替换。 */
@Repository
public class InMemorySourcingProjectRepository implements SourcingProjectRepository {

  private final Map<String, SourcingProject> projects = new ConcurrentHashMap<>();
  private final Map<String, Rfx> rfxs = new ConcurrentHashMap<>();
  private final AtomicLong seq = new AtomicLong(1);

  @Override
  public Long save(SourcingProject project) {
    if (project.getId() == null) {
      project.setId(seq.getAndIncrement());
    }
    projects.put(key(project.getTenantId(), project.getProjectNo()), project);
    return project.getId();
  }

  @Override
  public Long saveRfx(Rfx rfx) {
    if (rfx.getId() == null) {
      rfx.setId(seq.getAndIncrement());
    }
    rfxs.put(key(rfx.getTenantId(), rfx.getRfxNo()), rfx);
    return rfx.getId();
  }

  @Override
  public Optional<SourcingProject> findByProjectNo(Long tenantId, String projectNo) {
    return Optional.ofNullable(projects.get(key(tenantId, projectNo)));
  }

  @Override
  public Optional<Rfx> findRfxByNo(Long tenantId, String rfxNo) {
    return Optional.ofNullable(rfxs.get(key(tenantId, rfxNo)));
  }

  private String key(Long tenantId, String no) {
    return tenantId + ":" + no;
  }
}
