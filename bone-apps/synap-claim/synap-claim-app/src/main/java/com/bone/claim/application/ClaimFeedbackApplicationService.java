package com.bone.claim.application;

import com.bone.claim.adapter.web.dto.response.AiFeedbackEventResp;
import com.bone.claim.adapter.web.dto.response.AiFeedbackSummaryResp;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.feedback.AiFeedbackFact;
import com.bone.claim.domain.model.feedback.ClaimAiFeedbackEvent;
import com.bone.claim.domain.model.feedback.FeedbackCountRow;
import com.bone.claim.domain.repository.ClaimAiFeedbackEventRepository;
import com.bone.core.security.auth.CurrentOperator;
import com.bone.core.util.DistributedIdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI 建议曝光 / 人工裁决的服务端事实源。
// 只 append 一张表；不读案件、不写案件状态、不碰金额、不触发决策执行。
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimFeedbackApplicationService {

  public static final int SUMMARY_WINDOW_DAYS = 30;
  private static final int MAX_LIST = 50;

  private final ClaimAiFeedbackEventRepository repository;
  private final TenantPort tenantPort;

  @Transactional
  public AiFeedbackEventResp record(long claimId, AiFeedbackCommand cmd) {
    String problem = AiFeedbackRules.validate(cmd);
    if (problem != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_AI_FEEDBACK_INVALID, problem);
    }
    long tenantId = tenantPort.currentTenantId();
    ClaimAiFeedbackEvent prior = repository.findByEventId(tenantId, cmd.eventId());
    if (prior != null) {
      if (!samePayload(prior, claimId, cmd)) {
        throw ClaimErrors.of(
            ClaimErrorCodes.CLAIM_AI_FEEDBACK_CONFLICT,
            "eventId 已存在但载荷不同，拒绝覆盖已落库的审计事实: " + cmd.eventId());
      }
      return toResp(prior, true);
    }
    String actor = CurrentOperator.id().orElse("");
    AiFeedbackFact fact =
        new AiFeedbackFact(
            claimId,
            cmd.eventId(),
            cmd.eventType(),
            AiFeedbackRules.nvl(cmd.traceId()),
            cmd.involvement(),
            cmd.disposition(),
            cmd.reasonCode(),
            cmd.comment(),
            AiFeedbackRules.toModifiedFieldsJson(cmd.modifiedFields()),
            cmd.scene(),
            actor);
    Date now = new Date();
    ClaimAiFeedbackEvent event =
        ClaimAiFeedbackEvent.create(DistributedIdGenerator.generateLongId(), tenantId, fact, now);
    repository.insert(event);
    // 审计日志只记结构，不记备注 / 检索键 / PII。
    log.info(
        "[AI_FEEDBACK] eventType={} claimId={} involvement={} disposition={} tenantId={}",
        event.getEventType(),
        claimId,
        event.getInvolvement(),
        event.getDisposition(),
        tenantId);
    return toResp(event, false);
  }

  @Transactional(readOnly = true)
  public List<AiFeedbackEventResp> list(long claimId) {
    long tenantId = tenantPort.currentTenantId();
    return repository.findByClaimId(tenantId, claimId, MAX_LIST).stream()
        .map(e -> toResp(e, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public AiFeedbackSummaryResp summary() {
    long tenantId = tenantPort.currentTenantId();
    Timestamp since = Timestamp.from(Instant.now().minus(SUMMARY_WINDOW_DAYS, ChronoUnit.DAYS));
    Map<String, Long> counts = new HashMap<>();
    for (FeedbackCountRow row : repository.countByBucket(since)) {
      counts.merge(
          row.getBucket() == null ? "UNKNOWN" : row.getBucket(),
          row.getEventCount() == null ? 0L : row.getEventCount(),
          Long::sum);
    }
    long adopts = counts.getOrDefault("DISPOSITION_ADOPT", 0L);
    long rejects = counts.getOrDefault("DISPOSITION_REJECT", 0L);
    long modifies = counts.getOrDefault("DISPOSITION_MODIFY", 0L);
    log.info("[AI_FEEDBACK_SUMMARY] tenantId={} windowDays={}", tenantId, SUMMARY_WINDOW_DAYS);
    return new AiFeedbackSummaryResp(
        counts.getOrDefault("IMPRESSION", 0L),
        adopts + rejects + modifies,
        adopts,
        rejects,
        modifies,
        SUMMARY_WINDOW_DAYS,
        true);
  }

  private static boolean samePayload(ClaimAiFeedbackEvent e, long claimId, AiFeedbackCommand cmd) {
    return Objects.equals(e.getClaimId(), claimId)
        && Objects.equals(e.getEventType(), cmd.eventType())
        && Objects.equals(AiFeedbackRules.nvl(e.getInvolvement()), cmd.involvement())
        && Objects.equals(
            AiFeedbackRules.nvl(e.getDisposition()), AiFeedbackRules.nvl(cmd.disposition()))
        && Objects.equals(
            AiFeedbackRules.nvl(e.getReasonType()), AiFeedbackRules.nvl(cmd.reasonCode()))
        && Objects.equals(AiFeedbackRules.nvl(e.getScene()), cmd.scene())
        && Objects.equals(AiFeedbackRules.nvl(e.getTraceId()), AiFeedbackRules.nvl(cmd.traceId()));
  }

  private static AiFeedbackEventResp toResp(ClaimAiFeedbackEvent e, boolean replayed) {
    Date at = e.getCreatedAt();
    return new AiFeedbackEventResp(
        String.valueOf(e.getId()),
        e.getEventId(),
        String.valueOf(e.getClaimId()),
        e.getEventType(),
        e.getInvolvement(),
        e.getDisposition(),
        e.getReasonType(),
        e.getScene(),
        e.getActorId(),
        at == null ? null : at.toInstant(),
        replayed);
  }
}
