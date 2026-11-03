package com.bone.claim.application;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.event.ClaimSubmittedEvent;
import com.bone.claim.domain.model.stakeholder.ClaimStakeholder;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimStakeholderRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 报案干系人留痕记录器（§9.5 A1 / PIPL 29 条）。
 *
 * <p>订阅进程内 {@link ClaimSubmittedEvent}（报案受理成功必发，由 {@code ClaimCase.accept} 在原事务发布）， 在事务提交后按
 * {claimId, role} 维度 append 一笔 {@code t_claim_stakeholder}。写路径显式 {@link TenantContextRunner#runAs}
 * 声明租户（AFTER_COMMIT 后线程上下文已清空），同 {@code ClaimCaseTrackRecorder} / {@code ClaimReviewRecordRecorder}
 * 范式；零改动任何 emitter 与团队在途的报案/建案改造文件。
 *
 * <p><b>本记录器只落「被保人」这一条必然存在的干系人</b>：案件聚合仅持久化 {@code insured_id_hash} （PIPL 假名化锚点，明文姓名/手机/银行卡依规不落库——见
 * {@code ClaimStakeholder} 类注），故 INSURED 行以 {@code person_id_hash} 为身份锚、{@code name_masked}
 * 等遮蔽字段留空（非缺失，而是「明文不重复存储」的设计态）。 报案人 / 申请人 / 领款人通常在报案受理命令中随 KYC 一并提交，但其结构化字段当前尚未进入聚合——
 * 属团队在途的报案受理富化改造范围，待其落地后在受理入口同批补 recorder 调用即可，本次不触碰以免与在途工作冲突。
 *
 * <p><b>E-5.4 豁免</b>：本记录器仅 append 干系人台账（合规中间态），领域事件已由发起受理的聚合在原事务中发布， 此处不再派发领域事件。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimStakeholderRecorder {

  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimStakeholderRepository stakeholderRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onClaimSubmitted(ClaimSubmittedEvent event) {
    try {
      TenantContextRunner.runAs(
          event.tenantId(),
          () -> {
            ClaimCase claim = claimCaseRepository.findByClaimNo(event.tenantId(), event.claimNo());
            if (claim == null
                || claim.getInsuredIdHash() == null
                || claim.getInsuredIdHash().isBlank()) {
              // 未携带被保人假名化 ID（不应发生）：不写空锚点行，避免噪音台账。
              return;
            }
            // TODO(claim 干系人富化): 受理命令携报案人/申请人/领款人后，将下方「只落 INSURED」改为遍历事件中的
            //   stakeholder 列表按 role 逐条 create+save（ClaimStakeholder.create 入参已对齐，零新依赖）。
            stakeholderRepository.save(
                ClaimStakeholder.create(
                    event.tenantId(),
                    event.claimId(),
                    ClaimStakeholder.ROLE_INSURED,
                    claim.getInsuredIdHash(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    event.occurredAt()));
          });
    } catch (Exception e) {
      // 留痕落库失败不应回滚主流程；记录日志供人工 / 对账兜底。
      log.error("报案干系人留痕落库失败: claimId={}, tenantId={}", event.claimId(), event.tenantId(), e);
    }
  }
}
