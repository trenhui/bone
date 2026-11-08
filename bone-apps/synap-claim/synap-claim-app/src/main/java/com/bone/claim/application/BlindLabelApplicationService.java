package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.decision.AiConfidenceSource;
import com.bone.claim.domain.model.decision.DecisionExecution;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.model.evaluation.BlindLabelUnblindedEvent;
import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.InspectionRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 独立盲标应用服务（G4-1 数据飞轮的防反馈环闸门）。
 *
 * <p><b>为什么后督抽检的结论不能当金标准</b>：抽检员在流程内看得到 AI 建议——这正是抽检的工作方式， 但也让他的结论携带 AI 的影响（Sculley feedback
 * loops）。独立盲标要求标注人在看不到任何 AI 痕迹的前提下 重判同一案件，才能产出可信的「人 vs AI」成对样本。本服务把四道闸做成结构：
 *
 * <ol>
 *   <li><b>载荷禁区</b>：派发载荷（{@code label_payload_json}）由调用方给材料引用，但键名必须过 {@link
 *       BlindLabelTask#FORBIDDEN_PAYLOAD_KEYS} 扫描——含任一 AI 痕迹键即 422。禁区是代码纪律：往载荷里塞 {@code
 *       aiSuggestion} 直接派发失败，而不是「标注人自律别看」；
 *   <li><b>物理分离</b>：AI 建议快照在 {@link #unblind} 时才从决策轨迹读取写入。提交前表里没有任何 AI
 *       数据，绕过应用层直查库也看不到（区别于「有数据但不返回给前端」）；
 *   <li><b>标注人去污染</b>：该案既有抽检单的质检人与标注人同人即拒绝（409）——同一人先对着 AI 建议检过再「盲标」， 盲标已被既有印象污染，产出的是自我确认不是独立判定；
 *   <li><b>解盲才配对</b>：{@code PAIRED} 只表示成对样本产出，一致率由评测流水线离线判定（判定规则可升级重算， 不冻结在解盲事务里）。
 * </ol>
 *
 * <p><b>AI 参与口径复用单一真源</b>：{@code confidence_source == MODEL} 才算 AI 真实参与（{@link
 * AiConfidenceSource}：证明不了是模型就不许冒充模型）。桩/降级环境的「AI 建议快照」是假证据，解盲后只标 {@code AI_ABSENT}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlindLabelApplicationService {

  private final BlindLabelTaskRepository blindLabelTaskRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;
  private final InspectionRepository inspectionRepository;
  private final DecisionExecutionRepository decisionExecutionRepository;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;
  private final ObjectMapper objectMapper;

  /**
   * 派发盲标任务（PENDING）。
   *
   * @param labelPayloadJson 标注载荷 JSON（材料引用清单等；键名过禁区扫描）
   */
  @Transactional
  public BlindLabelView dispatch(long claimId, String assigneeId, String labelPayloadJson) {
    if (assigneeId == null || assigneeId.isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, "标注人不能为空");
    }
    long tenantId = tenantPort.currentTenantId();
    ClaimCase claim = claimCaseApplicationService.getClaimAggregate(claimId);
    if (claim == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    if (!Long.valueOf(tenantId).equals(claim.getTenantId())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId); // 跨租户按不存在处理，不泄露存在性
    }
    if (blindLabelTaskRepository.findActiveByClaimId(claimId) != null) {
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_CONFLICT, "案件存在未解盲的盲标任务: " + claimId);
    }
    // 标注人去污染：同一人对同一案先抽检（看得到 AI 建议）再盲标 ⇒ 盲标已被污染。
    InspectionRecord inspection = inspectionRepository.findLatestByClaimId(claimId);
    if (inspection != null
        && !inspection.getInspectorId().isBlank()
        && inspection.getInspectorId().equals(assigneeId)) {
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_ANNOTATOR_CONFLICT,
          "标注人与本案后督抽检质检人相同，盲标结论不可作为独立判定: " + inspection.getInspectionNo());
    }
    validatePayload(labelPayloadJson);
    long id = DistributedIdGenerator.generateLongId();
    BlindLabelTask task =
        BlindLabelTask.open(
            id,
            tenantId,
            "BL" + id,
            claimId,
            assigneeId,
            labelPayloadJson,
            sha256Hex(labelPayloadJson));
    blindLabelTaskRepository.insert(task);
    log.info(
        "[BLIND_LABEL] dispatched taskNo={} claimId={} assignee={} payloadHash={}",
        task.getTaskNo(),
        claimId,
        assigneeId,
        task.getPayloadHash());
    return toView(task);
  }

  /** 提交盲标结论（仅 PENDING 态一次；结论提交后不可改写）。 */
  @Transactional
  public BlindLabelView submit(long taskId, String labelResultJson) {
    BlindLabelTask task = requireTask(taskId);
    task.submit(labelResultJson, Instant.now());
    blindLabelTaskRepository.save(task);
    log.info(
        "[BLIND_LABEL] submitted taskNo={} claimId={} hash={}",
        task.getTaskNo(),
        task.getClaimId(),
        task.getPayloadHash());
    return toView(task);
  }

  /**
   * 解盲（LABELED → UNBLINDED）：此时才读取 AI 建议快照并配对，发布解盲事件。
   *
   * <p>E-5.4 配对：{@code save} 与 {@code publishFrom} 同事务——解盲事件是评测流水线的消费触发器， 只落库不发等于样本产出了却没人知道。
   */
  @Transactional
  public BlindLabelView unblind(long taskId) {
    BlindLabelTask task = requireTask(taskId);
    long tenantId = tenantPort.currentTenantId();
    DecisionExecution execution =
        decisionExecutionRepository.findLatestByClaimId(task.getClaimId());
    boolean aiParticipated =
        execution != null
            && AiConfidenceSource.MODEL.name().equals(execution.getConfidenceSource());
    String aiSuggestionJson = aiParticipated ? aiSnapshot(execution) : null;
    BlindLabelUnblindedEvent event =
        task.unblind(
            aiSuggestionJson,
            aiParticipated ? BlindLabelTask.Agreement.PAIRED : BlindLabelTask.Agreement.AI_ABSENT,
            Instant.now());
    blindLabelTaskRepository.save(task);
    domainEventPublisher.publishFrom(task);
    log.info(
        "[BLIND_LABEL] unblinded taskNo={} claimId={} agreement={} aiParticipated={}",
        task.getTaskNo(),
        task.getClaimId(),
        task.getAgreement(),
        aiParticipated);
    return toView(task, event);
  }

  /** 读单：解盲前不返回 AI 侧字段（防御性——虽然库内在解盲前本就没有 AI 数据）。 */
  public BlindLabelView get(long taskId) {
    BlindLabelTask task = blindLabelTaskRepository.findById(taskId);
    if (task == null) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_NOT_FOUND, taskId);
    }
    return toView(task);
  }

  public java.util.List<BlindLabelView> listByAssignee(String assigneeId) {
    return blindLabelTaskRepository.findRecentByAssignee(assigneeId).stream()
        .map(this::toView)
        .toList();
  }

  /** 载荷禁区扫描：顶层键命中任一 AI 痕迹键即拒绝。 */
  private void validatePayload(String labelPayloadJson) {
    if (labelPayloadJson == null || labelPayloadJson.isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, "标注载荷不能为空");
    }
    Map<?, ?> payload;
    try {
      payload = objectMapper.readValue(labelPayloadJson, Map.class);
    } catch (Exception e) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, "载荷必须是 JSON 对象");
    }
    if (!(payload instanceof Map<?, ?> map) || map.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, "载荷必须是 JSON 对象");
    }
    Set<String> forbidden = BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS;
    for (Object key : map.keySet()) {
      if (key != null && forbidden.contains(key.toString())) {
        throw ClaimErrors.of(
            ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, "载荷含 AI 痕迹键，盲标前提被破坏: " + key);
      }
    }
  }

  /** AI 建议快照：只取决策结论与溯源字段（不含提示词原文等长文本），供评测侧离线比对。 */
  private String aiSnapshot(DecisionExecution execution) {
    try {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("executionId", execution.getId());
      snapshot.put("decisionResult", execution.getDecisionResult());
      snapshot.put("decisionTier", execution.getDecisionTier());
      snapshot.put("aiConfidence", execution.getAiConfidence());
      snapshot.put("confidenceSource", execution.getConfidenceSource());
      snapshot.put("modelVersion", execution.getModelVersion());
      snapshot.put(
          "finishedAt",
          execution.getFinishedAt() == null ? null : execution.getFinishedAt().toString());
      return objectMapper.writeValueAsString(snapshot);
    } catch (Exception e) {
      // 快照序列化失败不该让解盲事务失败；降级为 null 等价 AI_ABSENT 会丢信息——宁可失败让人看到。
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_CONFLICT, "AI 建议快照序列化失败: " + e.getMessage());
    }
  }

  private BlindLabelTask requireTask(long taskId) {
    BlindLabelTask task = blindLabelTaskRepository.findById(taskId);
    if (task == null) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_NOT_FOUND, taskId);
    }
    return task;
  }

  private BlindLabelView toView(BlindLabelTask task) {
    return toView(task, null);
  }

  private BlindLabelView toView(BlindLabelTask task, BlindLabelUnblindedEvent event) {
    return new BlindLabelView(
        task.getId(),
        task.getTaskNo(),
        task.getClaimId(),
        task.getAssigneeId(),
        task.getStatus(),
        task.getLabelPayloadJson(),
        task.getPayloadHash(),
        task.getLabelResultJson(),
        task.getSubmittedAt(),
        // 解盲前（以及防御性地：非终态时）不返回 AI 侧字段。
        task.isUnblinded() ? task.getAiSuggestionJson() : null,
        task.isUnblinded() ? task.getAgreement() : null,
        task.getUnblindedAt(),
        event != null && event.aiParticipated());
  }

  private static String sha256Hex(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** 盲标任务视图。{@code aiSuggestionJson} / {@code agreement} 仅在解盲后有值——解盲前返回 null 不是脱敏（库里没有）， 是结构事实。 */
  public record BlindLabelView(
      Long id,
      String taskNo,
      Long claimId,
      String assigneeId,
      String status,
      String labelPayloadJson,
      String payloadHash,
      String labelResultJson,
      Instant submittedAt,
      String aiSuggestionJson,
      String agreement,
      Instant unblindedAt,
      boolean aiParticipated) {}
}
