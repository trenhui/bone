package com.bone.aitrace.infrastructure.event;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;

/**
 * 监听 gateway 发布的 {@link LlmCallRecorded}，落一次证据快照（进程内 Spring 事件，AFTER_COMMIT 由发布方事务保证）。
 *
 * <p>证据三元组在落库前就地计算：输入 SHA-256（防篡改）、请求/响应 IR（JSON）、引擎版本（模型）。租户由 {@link TenantProvider} 在
 * infrastructure 侧解析，网关本身保持租户无关。
 */
public class EvidenceTraceListener {

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
    repository.insert(toSnapshot(event, tenantProvider.currentTenantId()));
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
