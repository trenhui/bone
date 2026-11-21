package com.bone.procurement.sourcing.infrastructure;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.sourcing.Rfx;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受控内存仓储（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated gateway 端口已由 {@link SourcingProjectRepositoryAdapter} 委托 SDK 仓储实现；同接口保留 @Repository
 *     bean 会双 bean。
 */
@Deprecated
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
