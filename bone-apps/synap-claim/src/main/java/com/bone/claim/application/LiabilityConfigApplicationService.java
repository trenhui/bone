package com.bone.claim.application;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 责任配置应用服务（A4 配置端）。
 *
 * <p><b>版本化不变式（§8.2「已生效版本不可改」）</b>：已生效（ACTIVE）的责任<b>不可原地修改</b>， 对其编辑会生成<b>同 {@code liabilityUuid}
 * 的新版本</b>（DRAFT），旧版本冻结保留；发布新版本时自动将同 UUID 的旧 ACTIVE 版本置为 RETIRED，保证每个 UUID
 * 同时只有一个生效版本。草稿（DRAFT）可原地编辑。
 *
 * <p><b>与既有设计的关系</b>：此前 {@link LiabilityRepository} 约定"只读、配置由外部导入"；本服务在不破坏 读模型（理算）的前提下补上运营侧写入口（逐条维护
 * / 批量导入），属于该约定的有意演进。
 */
@Service
@RequiredArgsConstructor
public class LiabilityConfigApplicationService {

  private final LiabilityRepository liabilityRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  /** 新建责任（DRAFT 草稿态，版本 v1）。 */
  @Transactional
  public LiabilityView createLiability(LiabilityRequests.CreateLiabilityReq command) {
    long tenantId = tenantPort.currentTenantId();
    AdjLiability liability =
        AdjLiability.of(
            DistributedIdGeneratorHolder.next(),
            tenantId,
            UUID.randomUUID().toString(),
            "v1",
            command.planId(),
            command.liabilityCode(),
            command.liabilityType(),
            toDecimal(command.payPercent()),
            toDecimal(command.liabilityDeduct()),
            toDecimal(command.liabilityLimit()),
            toDecimal(command.invoiceDeduct()),
            toDecimal(command.adjustDeduct()),
            toDecimal(command.dailyLimit()),
            command.dailyLimitBasis(),
            command.limitMode(),
            command.limitHospitalCodes(),
            command.limitDiagnosisCodes(),
            command.catalogPolicy(),
            command.catalogCode(),
            command.socialOnly(),
            command.waitingPeriod(),
            command.status() == null || command.status().isBlank() ? "DRAFT" : command.status());
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    return LiabilityView.from(liability);
  }

  /** 编辑责任：ACTIVE 生成同 UUID 新版本（DRAFT），DRAFT 原地编辑。空字段沿用原值。 */
  @Transactional
  public LiabilityView updateLiability(
      long liabilityId, LiabilityRequests.UpdateLiabilityReq command) {
    AdjLiability existing = requireLiability(liabilityId);
    long tenantId = tenantPort.currentTenantId();
    boolean active = existing.isActive();
    AdjLiability next =
        AdjLiability.of(
            active ? DistributedIdGeneratorHolder.next() : liabilityId,
            tenantId,
            existing.getLiabilityUuid(),
            active ? nextVersion(existing.getLiabilityVersion()) : existing.getLiabilityVersion(),
            existing.getPlanId(),
            or(command.liabilityCode(), existing.getLiabilityCode()),
            or(command.liabilityType(), existing.getLiabilityType()),
            num(command.payPercent(), existing.getPayPercent()),
            num(command.liabilityDeduct(), existing.getLiabilityDeduct()),
            num(command.liabilityLimit(), existing.getLiabilityLimit()),
            num(command.invoiceDeduct(), existing.getInvoiceDeduct()),
            num(command.adjustDeduct(), existing.getAdjustDeduct()),
            num(command.dailyLimit(), existing.getDailyLimit()),
            or(command.dailyLimitBasis(), existing.getDailyLimitBasis()),
            or(command.limitMode(), existing.getLimitMode()),
            or(command.limitHospitalCodes(), existing.getLimitHospitalCodes()),
            or(command.limitDiagnosisCodes(), existing.getLimitDiagnosisCodes()),
            or(command.catalogPolicy(), existing.getCatalogPolicy()),
            or(command.catalogCode(), existing.getCatalogCode()),
            command.socialOnly() != null ? command.socialOnly() : existing.getSocialOnly(),
            command.waitingPeriod() != null ? command.waitingPeriod() : existing.getWaitingPeriod(),
            active ? "DRAFT" : existing.getStatus());
    liabilityRepository.save(next);
    domainEventPublisher.publishFrom(next);
    return LiabilityView.from(next);
  }

  /** 发布责任（DRAFT → ACTIVE）：同时将该 UUID 旧的 ACTIVE 版本置为 RETIRED，保证唯一生效版本。 */
  @Transactional
  public LiabilityView publishLiability(long liabilityId) {
    AdjLiability liability = requireLiability(liabilityId);
    if ("RETIRED".equals(liability.getStatus())) {
      throw new DomainException("责任已下线，不可发布");
    }
    AdjLiability oldActive = liabilityRepository.findActiveByUuid(liability.getLiabilityUuid());
    if (oldActive != null && !oldActive.getId().equals(liability.getId())) {
      oldActive.retire();
      liabilityRepository.save(oldActive);
      domainEventPublisher.publishFrom(oldActive);
    }
    liability.publish();
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    return LiabilityView.from(liability);
  }

  /** 下线责任（ACTIVE/DRAFT → RETIRED）。 */
  @Transactional
  public LiabilityView retireLiability(long liabilityId) {
    AdjLiability liability = requireLiability(liabilityId);
    liability.retire();
    liabilityRepository.save(liability);
    domainEventPublisher.publishFrom(liability);
    return LiabilityView.from(liability);
  }

  /** 按计划列出全部责任（含各版本，配置面树形展示用）。 */
  @Transactional(readOnly = true)
  public List<LiabilityView> listByPlan(long planId) {
    return liabilityRepository.findByPlanId(planId).stream().map(LiabilityView::from).toList();
  }

  // ===================== 内部 =====================

  private AdjLiability requireLiability(long id) {
    AdjLiability l = liabilityRepository.findById(id);
    if (l == null) {
      throw new DomainException("责任不存在: " + id);
    }
    return l;
  }

  private static String nextVersion(String current) {
    if (current == null || current.isBlank()) {
      return "v1";
    }
    int idx = current.lastIndexOf('v');
    String numPart = idx >= 0 ? current.substring(idx + 1) : current;
    try {
      return "v" + (Integer.parseInt(numPart.trim()) + 1);
    } catch (NumberFormatException e) {
      return current + "-rev";
    }
  }

  private static String or(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static BigDecimal toDecimal(String s) {
    if (s == null || s.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(s.trim());
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }

  private static BigDecimal num(String s, BigDecimal fallback) {
    if (s == null || s.isBlank()) {
      return fallback;
    }
    return toDecimal(s);
  }

  /** 雪花 ID 生成（避免在此类直接耦合 {@code DistributedIdGenerator} 静态工具，统一收口）。 */
  private static final class DistributedIdGeneratorHolder {
    private DistributedIdGeneratorHolder() {}

    static long next() {
      return com.bone.core.util.DistributedIdGenerator.generateLongId();
    }
  }

  /** 责任视图（配置面读模型；放在 application 层因其为给 adapter 消费的投影）。 */
  public record LiabilityView(
      String liabilityId,
      String liabilityUuid,
      String liabilityVersion,
      String planId,
      String liabilityCode,
      String liabilityType,
      String payPercent,
      String liabilityDeduct,
      String liabilityLimit,
      String invoiceDeduct,
      String adjustDeduct,
      String dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      Integer socialOnly,
      Integer waitingPeriod,
      String status) {

    static LiabilityView from(AdjLiability l) {
      return new LiabilityView(
          String.valueOf(l.getId()),
          l.getLiabilityUuid(),
          l.getLiabilityVersion(),
          String.valueOf(l.getPlanId()),
          l.getLiabilityCode(),
          l.getLiabilityType(),
          plain(l.getPayPercent()),
          plain(l.getLiabilityDeduct()),
          plain(l.getLiabilityLimit()),
          plain(l.getInvoiceDeduct()),
          plain(l.getAdjustDeduct()),
          plain(l.getDailyLimit()),
          l.getDailyLimitBasis(),
          l.getLimitMode(),
          l.getLimitHospitalCodes(),
          l.getLimitDiagnosisCodes(),
          l.getCatalogPolicy(),
          l.getCatalogCode(),
          l.getSocialOnly(),
          l.getWaitingPeriod(),
          l.getStatus());
    }

    private static String plain(BigDecimal v) {
      return v == null ? "0" : v.toPlainString();
    }
  }
}
