package com.bone.claim.application;

import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 保司推送发件箱写入器（补齐 ADR-0021「跨进程事件必须 Outbox、与业务同事务落库」缺口）。
 *
 * <p><b>为什么需要它</b>：结案（FINISHED）后要把赔案结论报送承保保司。此前 {@code t_domain_event_outbox} 表只有中继（{@link
 * OutboxRelayJob}）与统计读取，主代码无任何写入方——事件零丢失链路缺了「写」这一半。 本写入器在支付结案的<b>同一事务</b>内追加一条 {@code INSURER_PUSH}
 * 记录：保存成功则推送事件必在库， 由既有中继按 PENDING→DISPATCHING→SENT/FAILED 投递，失败退避重试、耗尽进死信告警，全程可观测。
 *
 * <p><b>幂等</b>：同一赔案（aggregateId=claimId）只有一条 {@code INSURER_PUSH}。重复结案/重复支付回调
 * 在写入前按聚合+事件类型查重命中即跳过，杜绝二次报送。
 *
 * <p><b>@NoDomainEvent</b>：本服务只写发件箱台账，不再向外发布领域事件，避免「事件→写 outbox→再发事件」的递归。
 */
@Slf4j
@Component
@NoDomainEvent
@RequiredArgsConstructor
public class InsurerPushWriter {

  /** 聚合类型：赔案。 */
  public static final String AGGREGATE_CLAIM = "ClaimCase";

  /** 事件类型：结案报送保司。 */
  public static final String EVENT_INSURER_PUSH = "INSURER_PUSH";

  private final DomainEventOutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  /**
   * 在业务事务内登记一条保司推送事件（幂等：同赔案已登记则跳过）。
   *
   * @return 本次是否真的写入了新记录（true=新写入；false=幂等跳过）
   */
  @Transactional
  public boolean appendOnFinish(
      Long tenantId,
      Long claimId,
      String claimNo,
      String reportNo,
      String policyNo,
      String insurerPolicyNo,
      String insurerCode,
      String insurerName,
      BigDecimal amount,
      String operatorId) {
    DomainEventOutbox existing =
        outboxRepository.findLatestByAggregateAndEventType(
            AGGREGATE_CLAIM, claimId, EVENT_INSURER_PUSH);
    if (existing != null) {
      log.info("保司推送事件幂等跳过: claimId={} outboxId={}", claimId, existing.getId());
      return false;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("claimId", claimId);
    payload.put("claimNo", claimNo);
    payload.put("reportNo", reportNo);
    payload.put("policyNo", policyNo);
    payload.put("insurerPolicyNo", insurerPolicyNo == null ? "" : insurerPolicyNo);
    payload.put("insurerCode", insurerCode == null ? "" : insurerCode);
    payload.put("insurerName", insurerName == null ? "" : insurerName);
    payload.put("amount", amount == null ? null : amount.toPlainString());
    payload.put("pushedAt", Instant.now().toString());
    payload.put("operatorId", operatorId == null ? "" : operatorId);

    DomainEventOutbox outbox =
        DomainEventOutbox.of(
            tenantId == null ? 0L : tenantId,
            AGGREGATE_CLAIM,
            claimId,
            EVENT_INSURER_PUSH,
            toJson(payload));
    // 身份/审计列由聚合自身工厂回填（E-8：外层不得篡改聚合身份）；本次写入只经仓储 save。
    outboxRepository.save(outbox);
    log.info("保司推送事件已写入 Outbox: claimId={} claimNo={}", claimId, claimNo);
    return true;
  }

  /** 保司推送状态视图（运营/联调查询用）。 */
  public record PushStatus(
      Long outboxId,
      Long claimId,
      String claimNo,
      String status,
      String payload,
      Integer retryCount,
      String createdAt) {}

  /** 查询某赔案的保司推送记录（无则 null）。租户隔离由仓储 AUTO 作用域 + 上下文落实。 */
  @Transactional(readOnly = true)
  public PushStatus findByClaim(Long claimId) {
    DomainEventOutbox outbox =
        outboxRepository.findLatestByAggregateAndEventType(
            AGGREGATE_CLAIM, claimId, EVENT_INSURER_PUSH);
    if (outbox == null) {
      return null;
    }
    return new PushStatus(
        outbox.getId(),
        outbox.getAggregateId(),
        extractClaimNo(outbox.getPayload()),
        outbox.getStatus(),
        outbox.getPayload(),
        outbox.getRetryCount(),
        outbox.getCreatedAt() == null ? null : outbox.getCreatedAt().toString());
  }

  private String extractClaimNo(String payload) {
    if (payload == null) {
      return "";
    }
    try {
      Map<?, ?> m = objectMapper.readValue(payload, Map.class);
      Object v = m.get("claimNo");
      return v == null ? "" : String.valueOf(v);
    } catch (JsonProcessingException e) {
      return "";
    }
  }

  private String toJson(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("保司推送事件序列化失败", e);
    }
  }
}
