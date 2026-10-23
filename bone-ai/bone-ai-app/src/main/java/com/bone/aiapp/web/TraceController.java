package com.bone.aiapp.web;

import com.bone.aiapp.config.AiTenantContext;
import com.bone.aitrace.application.EvidenceQueryApplicationService;
import com.bone.aitrace.common.AiTraceErrorCodes;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.core.exception.BizException;
import com.bone.core.model.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产拓扑：证据链 REST 面（审计 / 法务 / 产品自查消费，详设附录 A「证据回放」落地）。
 *
 * <p>此前回放只有进程内 {@link EvidenceQueryApplicationService}，审计侧要证据必须进产品进程——本控制器把回放暴露为
 * HTTP，越权防护与全模块同构：<b>租户从上下文取、快照租户不匹配即拒</b>（traceId 是 UUID 猜不中，但跨租户命中必须显式拒绝而非
 * 静默返回空——空结果会让「查不到」与「不许查」无法区分，排障与取证都会误判）。
 *
 * <p>只读面：证据链的核心不变式是「无旁路写入」，故本控制器刻意不提供任何写端点（写只可能发生在 gateway → trace 监听落库链路）。
 */
@RestController
@RequestMapping("/api/ai/v1/trace")
public class TraceController {

  private final EvidenceQueryApplicationService evidenceQuery;
  private final AiTenantContext tenantContext;

  public TraceController(
      EvidenceQueryApplicationService evidenceQuery, AiTenantContext tenantContext) {
    this.evidenceQuery = evidenceQuery;
    this.tenantContext = tenantContext;
  }

  /**
   * 按 traceId 回放一次调用的全部证据快照（输入哈希 + IR + 引擎版本，按时间序）。
   *
   * <p>一次 AI 建议可能落多条快照（LLM 调用 / 短路 / 失败各自留痕），故返回列表；空列表 = 该 traceId 无证据
   * （含「从未发生」与「已过期清理」两种，由调用方结合业务判断）。
   */
  @GetMapping("/{traceId}")
  public ApiResponse<List<EvidenceSnapshot>> replay(@PathVariable String traceId) {
    String tenantId = tenantContext.requireTenantId();
    List<EvidenceSnapshot> snapshots = evidenceQuery.replayByTraceId(traceId);
    // 跨租户命中显式拒绝：任一快照不属于当前租户即整体拒（traceId 维度不存在混合租户的合法场景）
    boolean crossTenant =
        snapshots.stream().anyMatch(s -> !tenantId.equals(String.valueOf(s.getTenantId())));
    if (crossTenant) {
      throw new BizException(
          403, "证据不属于当前租户，拒绝回放: traceId=" + traceId, AiTraceErrorCodes.TENANT_DENIED);
    }
    return ApiResponse.success(snapshots);
  }

  /** 按快照 ID 取单条（审计抽查定位到具体一条时用；同样做租户校验）。 */
  @GetMapping("/item/{id}")
  public ApiResponse<EvidenceSnapshot> byId(@PathVariable Long id) {
    String tenantId = tenantContext.requireTenantId();
    EvidenceSnapshot snapshot = evidenceQuery.byId(id);
    if (snapshot == null) {
      throw new BizException(404, "证据快照不存在: id=" + id, AiTraceErrorCodes.NOT_FOUND);
    }
    if (!tenantId.equals(String.valueOf(snapshot.getTenantId()))) {
      throw new BizException(403, "证据不属于当前租户，拒绝访问: id=" + id, AiTraceErrorCodes.TENANT_DENIED);
    }
    return ApiResponse.success(snapshot);
  }
}
