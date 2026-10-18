package com.bone.aitrace.infrastructure.event;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * 监听 gateway 发布的 {@link LlmCallRecorded}，落一次证据快照（进程内 Spring 事件，AFTER_COMMIT 由发布方事务保证）。
 *
 * <p>证据三元组在落库前就地计算：输入 SHA-256（防篡改）、请求/响应 IR（JSON）、引擎版本（模型）。租户由 {@link TenantProvider} 在
 * infrastructure 侧解析，网关本身保持租户无关。
 *
 * <p><b>租户缺失显式拒绝（2026-10-17 复核修复，详设 P4 定案）</b>：上下文无租户（或非数值编码）时抛 {@link
 * #ERROR_TENANT_MISSING}——「落一条租户不明的合规证据」比「拒返」更危险（审计链上出现无法归属的快照即失真），故 fail-closed； 网关门面照常拒返，调用方转
 * HITL / 确定性路径。
 */
public class EvidenceTraceListener {

  /** 租户上下文缺失稳定业务码（fail-closed：不落租户不明的证据；trace 模块自有码，不跨模块借码）。 */
  public static final String ERROR_TENANT_MISSING = "AI_TRACE_TENANT_CONTEXT_MISSING";

  private static final Logger log = LoggerFactory.getLogger(EvidenceTraceListener.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final EvidenceSnapshotRepository repository;
  private final TenantProvider tenantProvider;

  public EvidenceTraceListener(
      EvidenceSnapshotRepository repository, TenantProvider tenantProvider) {
    this.repository = repository;
    this.tenantProvider = tenantProvider;
  }

  @EventListener
  public void onLlmCallRecorded(LlmCallRecorded event) {
    EvidenceSnapshot snapshot = toSnapshot(event, requireTenantId());
    if (alreadyRecorded(snapshot)) {
      // 幂等消费（ClaimSphere §10.2 / 业界事件可靠性实践）：同一事件重复投递不重复留证——
      // 重复快照会让审计链出现同一次调用的多条记录，回放与成本对账口径失真
      log.info(
          "证据事件重复投递，跳过落库（幂等）: traceId={}, inputHash={}",
          snapshot.getTraceId(),
          snapshot.getInputHash());
      return;
    }
    repository.insert(snapshot);
  }

  /**
   * 幂等判重：同一 traceId 下已存在相同输入哈希的快照即视为重复事件重放。
   *
   * <p>用 {@code traceId + inputHash} 而非仅 traceId：一次业务动作可能包含多次 LLM 调用（多轮编排共用 traceId），
   * 输入哈希相同才说明是同一次调用重放；不同输入（不同轮次）仍各自留证。
   *
   * <p><b>局限（已知，不假装已解决）</b>：查重与插入之间存在并发窗口——两个相同事件同时进入时可能都查不到而双写。 彻底兜底需给 {@code
   * ai_trace_snapshot(trace_id, input_hash)} 加唯一索引（DDL 属 AGENTS L3，待审批）； 当前 Spring
   * 进程内同步事件下单线程投递，窗口实际不可达，此判重先挡住重试 / 补偿队列重放这一类真实重复。
   */
  private boolean alreadyRecorded(EvidenceSnapshot snapshot) {
    return repository.findByTraceId(snapshot.getTraceId()).stream()
        .anyMatch(existing -> snapshot.getInputHash().equals(existing.getInputHash()));
  }

  private Long requireTenantId() {
    Long tenantId = tenantProvider.currentTenantId();
    if (tenantId == null) {
      throw new BizException(500, "证据链落盘拒绝：租户上下文缺失或非数值编码（不得落租户不明的合规快照）", ERROR_TENANT_MISSING);
    }
    return tenantId;
  }

  static EvidenceSnapshot toSnapshot(LlmCallRecorded event, Long tenantId) {
    Map<String, Object> input = inputMap(event.request());
    String inputHash = sha256(writeJson(input));
    String engineVersion =
        event.response() != null && event.response().model() != null
            ? event.response().model()
            : event.request().model();

    Map<String, Object> ir = new LinkedHashMap<>();
    ir.put("traceId", event.traceId());
    ir.put("occurredAt", String.valueOf(event.occurredAt()));
    ir.put("engineVersion", engineVersion);
    ir.put("input", input);
    ir.put("output", outputMap(event.response()));
    if (event.errorMessage() != null) {
      ir.put("error", event.errorMessage()); // 失败调用留证（事故复盘直接证据）
    }

    return EvidenceSnapshot.record(
        null,
        tenantId,
        event.traceId(),
        EvidenceType.LLM_CALL,
        inputHash,
        engineVersion,
        writeJson(ir));
  }

  private static Map<String, Object> inputMap(LlmRequest request) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("model", request.model());
    List<Map<String, String>> messages = new ArrayList<>();
    for (LlmMessage message : request.messages()) {
      Map<String, String> m = new LinkedHashMap<>();
      m.put("role", message.role());
      m.put("content", message.content());
      messages.add(m);
    }
    input.put("messages", messages);
    input.put("temperature", request.temperature());
    input.put("maxTokens", request.maxTokens());
    return input;
  }

  private static Map<String, Object> outputMap(LlmResponse response) {
    Map<String, Object> output = new LinkedHashMap<>();
    if (response == null) {
      return output;
    }
    output.put("content", response.content());
    output.put("model", response.model());
    output.put("finishReason", response.finishReason());
    if (response.usage() != null) {
      Map<String, Object> usage = new LinkedHashMap<>();
      usage.put("promptTokens", response.usage().promptTokens());
      usage.put("completionTokens", response.usage().completionTokens());
      usage.put("totalTokens", response.usage().totalTokens());
      output.put("usage", usage);
    }
    return output;
  }

  private static String writeJson(Map<String, Object> map) {
    try {
      return MAPPER.writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException("序列化证据 IR 失败: " + e.getMessage(), e);
    }
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new IllegalStateException("计算输入哈希失败", e);
    }
  }
}
