package com.bone.procurement.sourcing.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.sourcing.Rfx;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import java.math.BigDecimal;
import java.util.Date;
import org.springframework.stereotype.Service;

/**
 * 寻源应用服务（骨架）。AI 评分仅作建议，授标最终确认必须具名人工操作人。
 *
 * <p>@NoDomainEvent：本批为内部状态迁移与终态登记，属 E-5.4 豁免，不发布领域事件。
 */
@Service
@NoDomainEvent
public class SourcingApplicationService {

  private final SourcingProjectRepository repository;

  public SourcingApplicationService(SourcingProjectRepository repository) {
    this.repository = repository;
  }

  public Long createProject(
      Long tenantId,
      String projectNo,
      String categoryId,
      BigDecimal budgetAmount,
      String currency,
      String strategy,
      String ownerId,
      String rfxNo,
      String rfxType,
      Date dueAt) {
    if (repository.findByProjectNo(tenantId, projectNo).isPresent()) {
      throw new IllegalStateException("项目号已存在（幂等冲突）");
    }
    SourcingProject project =
        SourcingProject.create(
            tenantId, projectNo, categoryId, budgetAmount, currency, strategy, ownerId);
    Long id = repository.save(project);
    Rfx rfx = Rfx.create(tenantId, id, rfxNo, rfxType, dueAt);
    repository.saveRfx(rfx);
    return id;
  }

  public void publishRfx(Long tenantId, String rfxNo) {
    Rfx rfx =
        repository
            .findRfxByNo(tenantId, rfxNo)
            .orElseThrow(() -> new IllegalArgumentException("RFx 不存在"));
    rfx.publish(new Date());
    repository.saveRfx(rfx);
  }

  /** 人工授标：operator 必须来自登录态，AI 建议不得调用此入口（红线）。 */
  public void awardByHuman(Long tenantId, String projectNo, String operator) {
    SourcingProject project =
        repository
            .findByProjectNo(tenantId, projectNo)
            .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
    project.awardByHuman(operator);
    repository.save(project);
  }
}
