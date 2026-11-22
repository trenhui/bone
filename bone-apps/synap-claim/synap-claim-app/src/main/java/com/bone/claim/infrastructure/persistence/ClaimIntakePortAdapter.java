package com.bone.claim.infrastructure.persistence;

import com.bone.claim.application.port.out.ClaimIntakePort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ClaimIntakePort} 的持久化实现：「报案 + 首案」同事务写入。
 *
 * <p><b>为什么用 {@code REQUIRED} 而不是 {@code REQUIRES_NEW}</b>：外层 {@code
 * ClaimCaseApplicationService#submit} 已经是写事务（同一事务里还要做保单前置校验、码解析与事件发布）。
 * 开新事务会让报案与首案脱离外层上下文——外层回滚时这两行反而留下，正是本端口要消除的残留。 因此这里只声明 {@code
 * REQUIRED}：有外层就加入，没有（如未来被别的调用方直接用）就自建。
 *
 * <p><b>写入顺序不可调换</b>：赔案 {@code report_id} 是 NOT NULL 且指向报案主键。先写赔案会在库上直接撞
 * 外键语义（无外键约束时则是悬空引用），且失败后再写报案会留下孤儿赔案。
 */
@Component
@RequiredArgsConstructor
public class ClaimIntakePortAdapter implements ClaimIntakePort {

  private final ClaimReportRepository claimReportRepository;
  private final ClaimCaseRepository claimCaseRepository;

  @Override
  @Transactional
  public void persistIntake(ClaimReport report, ClaimCase firstCase) {
    claimReportRepository.insert(report);
    claimCaseRepository.insert(firstCase);
  }
}
