package com.bone.aiapp.config;

import com.bone.airag.domain.gateway.RagEventSink;
import com.bone.airag.domain.model.RagEvent;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RAG 检索事件下沉到 bone-ai-trace 的落库适配（R6，2026-10-24）：把 {@link RagEvent} 落为 {@link
 * EvidenceType#RAG_EVENT} 证据快照，替换 rag 默认的 {@code LoggingRagEventSink}（结构化 WARN）。
 *
 * <p><b>为什么下沉到证据快照而非只打日志</b>：检索降级（嵌入回落 BM25 / 重排降级 / 作用域超限 / 慢检索）是「能力档位悄然下降」的信号， 日志留不住趋势，也无法与 trace
 * 落盘成功率 ≥99.9% 同一条告警线对账。落为证据快照后，降级率可被检索、聚合、回放。
 *
 * <p><b>与 {@code EvidenceTraceListener} 的姿态差异（刻意的，不是疏漏）</b>：
 *
 * <ul>
 *   <li>LLM 调用证据是<b>合规铁证</b>——租户缺失即 fail-closed 拒落（落一条租户不明的证据比拒返更危险）；
 *   <li>RAG 事件是<b>运行时观测信号</b>——{@link RagEventSink} 契约明定「实现方不得抛异常吞掉检索，读路径可用性优先」。 故租户缺失时 <b>WARN
 *       后跳过</b>（不是抛异常）：一条降级事件漏记是可接受的观测损失，为它中断用户的检索请求不是。
 * </ul>
 *
 * <p><b>故障隔离</b>：{@link #record} 的任何异常都在本地吞掉并降级为 WARN——检索链不能因为「审计设施抖动」而不可用。
 *
 * <p><b>装配</b>：由 {@code RagTraceConfig} 以 {@code @Bean} 在 {@code evidenceSnapshotRepository}
 * 存在时注册（覆盖 rag 自动配置的默认 sink）；未接入 trace 持久化的上下文回落 {@code LoggingRagEventSink}，检索链零改动。
 */
public class TraceRagEventSink implements RagEventSink {

  private static final Logger log = LoggerFactory.getLogger(TraceRagEventSink.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 引擎版本位（RAG 事件无模型档位，用固定标识标明证据来源，回放时据此区分 LLM_CALL 与 RAG_EVENT）。 */
  static final String ENGINE_VERSION = "bone-ai-rag-event";

  private final EvidenceSnapshotRepository repository;
  private final TenantProvider tenantProvider;

  public TraceRagEventSink(EvidenceSnapshotRepository repository, TenantProvider tenantProvider) {
    this.repository = repository;
    this.tenantProvider = tenantProvider;
  }

  @Override
  public void record(RagEvent event) {
    if (event == null) {
      return;
    }
    Long tenantId;
    try {
      tenantId = tenantProvider.currentTenantId();
    } catch (RuntimeException e) {
      // 取租户本身故障（上下文损坏等）：同样不冲出检索链
      log.warn("RAG 事件取租户上下文失败，跳过落库: type={}, err={}", event.type(), e.toString());
      return;
    }
    if (tenantId == null) {
      // 与 LLM 证据的 fail-closed 不同：观测信号丢失可接受，中断检索不可接受
      log.warn(
          "RAG 事件租户上下文缺失，跳过落库（读路径可用性优先，不抛异常）: type={}, detail={}", event.type(), event.detail());
      return;
    }
    try {
      repository.insert(toSnapshot(event, tenantId));
    } catch (RuntimeException e) {
      // 审计设施抖动不得影响检索：降级为 WARN，事件内容仍在日志里可追
      log.warn(
          "RAG 事件落库失败（降级为日志，不影响检索）: type={}, detail={}, err={}",
          event.type(),
          event.detail(),
          e.toString());
    }
  }

  /**
   * 把事件构造为证据快照（输入哈希 = 载荷 SHA-256，防篡改；traceId 逐事件生成——{@link RagEvent} 不携带请求 traceId， 伪造一个关联 id
   * 会让回放查询产生假关联，宁可每条事件独立成证）。
   */
  static EvidenceSnapshot toSnapshot(RagEvent event, Long tenantId) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventType", event.type());
    payload.put("detail", event.detail());
    payload.put("occurredAt", LocalDateTime.now().toString());
    String json = writeJson(payload);
    return EvidenceSnapshot.record(
        null,
        tenantId,
        UUID.randomUUID().toString(),
        EvidenceType.RAG_EVENT,
        sha256(json),
        ENGINE_VERSION,
        json);
  }

  private static String writeJson(Map<String, Object> map) {
    try {
      return MAPPER.writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException("序列化 RAG 事件载荷失败: " + e.getMessage(), e);
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
      throw new IllegalStateException("计算 RAG 事件哈希失败", e);
    }
  }
}
