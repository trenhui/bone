package com.bone.metadata.runtime.application;

import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimePageQuery;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 运行时记录应用服务（ADR-0028 Application Service First）。
 *
 * <p><b>为什么存在</b>：原 {@code RuntimeRecordController} / {@code RuntimeRelationController} 各自直注 {@link
 * JdbcRuntimeRecordService} 并内联横切（{@code currentOperator} 在两个控制器各复制一份、行级 ACL owner 判定、状态机守卫调用、ID
 * 生成）。横切复制的归宿是漂移——两份 {@code currentOperator} 改其一即静默改变 另一处的审计写入口径。本类把「记录编排 + 横切判定」收敛为 application
 * 层唯一入口，控制器只留 HTTP 适配 （参数绑定、ETag/If-Match 头、幂等重放、ResponseEntity 组装），与 {@link RuntimeActionService}
 * 同构。
 *
 * <p><b>与 RuntimeActionService 的分工</b>：本类管「单表记录 CRUD + 横切判定」；动作（状态机流转）在 {@code
 * RuntimeActionService}。两者共享同一个 {@code JdbcRuntimeRecordService} 实例（Spring 单例）， 不引入第二份持久化路径。
 */
@Service
@RequiredArgsConstructor
public class RuntimeRecordApplicationService {

  private final JdbcRuntimeRecordService runtimeRecordService;
  private final TenantProvider tenantProvider;
  private final RuntimeStatusGuard statusGuard;
  private final RuntimeAccessGuard accessGuard;

  /** 当前租户（控制器原 {@code tenantProvider.currentTenantId()} 直呼的收敛点）。 */
  public long currentTenantId() {
    return tenantProvider.currentTenantId();
  }

  /**
   * 审计操作者标识（横切唯一真源，替代两个控制器各自复制的 private static currentOperator）。
   *
   * <p>优先级：① 认证详情中的数值 userId（JWT 的 userId claim，审计所需的 BIGINT 用户 ID，见 {@code
   * JwtAuthenticationFilter} 写入 details）；② 主体名（仅当其本身为数值时）。 未认证、匿名主体（dev 关闭安全时为
   * anonymousUser）或主体名非数值时返回 null —— 由 {@code JdbcRuntimeRecordService#stampAuditColumns} 负责跳过
   * BIGINT 审计列、仅注入时间戳，避免把字符串写入 BIGINT 报 "Incorrect integer value"。
   */
  public String currentOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return null;
    }
    Object details = auth.getDetails();
    if (details instanceof Number) {
      return details.toString();
    }
    if (details instanceof String s && s.matches("\\d+")) {
      return s;
    }
    String name = auth.getName();
    if (name != null && name.matches("\\d+")) {
      return name;
    }
    // 主体名为非数值（如 username）时仍返回，交由下层按数值校验跳过 BIGINT 列
    return name;
  }

  /**
   * 行级 ACL 操作者判定（Runtime §4.7.1）：平台管理员传 null（豁免）；非数值主体传 null（与 BIGINT created_by
   * 无可比性，按既有审计语义不启用）；否则传数值用户 ID，数据面按本人过滤。
   */
  public Long rowOwnerId() {
    if (accessGuard.isPlatformAdmin()) {
      return null;
    }
    String op = currentOperator();
    if (op == null || !op.matches("\\d+")) {
      return null;
    }
    return Long.parseLong(op);
  }

  /** 新记录 ID（集中生成点：控制器不再各自 import DistributedIdGenerator）。 */
  public long newRecordId() {
    return DistributedIdGenerator.generateLongId();
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, int page, int size, RuntimePageQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    return runtimeRecordService.page(entityCode, tenantId, page, size, query, rowOwnerId());
  }

  public PageResult<Map<String, Object>> pageDeleted(
      String entityCode, int page, int size, RuntimePageQuery query) {
    long tenantId = tenantProvider.currentTenantId();
    return runtimeRecordService.pageDeleted(entityCode, tenantId, page, size, query);
  }

  public Map<String, Object> getById(String entityCode, String id) {
    long tenantId = tenantProvider.currentTenantId();
    return runtimeRecordService.getById(entityCode, tenantId, id, rowOwnerId());
  }

  /**
   * 创建记录（含状态机守卫：客户端不得指定状态字段，指定即 422；入口状态由写路径按转移矩阵注入）。
   *
   * <p>守卫在本方法（应用层）而非控制器：状态机规则是业务规则，适配层不承载（G14 收口）。
   */
  public Map<String, Object> create(String entityCode, Map<String, Object> body) {
    long tenantId = tenantProvider.currentTenantId();
    statusGuard.applyCreateGuard(tenantId, entityCode, body);
    return runtimeRecordService.create(
        entityCode, tenantId, body, newRecordId(), currentOperator());
  }

  /** 更新记录（含状态机守卫：状态字段只允许经动作端点流转，客户端携带即 422）。 */
  public Map<String, Object> update(
      String entityCode, String id, Map<String, Object> body, Integer expectedVersion) {
    long tenantId = tenantProvider.currentTenantId();
    statusGuard.applyUpdateGuard(tenantId, entityCode, body);
    return runtimeRecordService.update(
        entityCode, tenantId, id, body, expectedVersion, currentOperator());
  }

  /** 归档（软删）。If-Match 版本由控制器解析后传入（HTTP 头语义留在适配层）。 */
  public void delete(String entityCode, String id, Integer expectedVersion) {
    long tenantId = tenantProvider.currentTenantId();
    runtimeRecordService.delete(entityCode, tenantId, id, expectedVersion, currentOperator());
  }

  /** 恢复归档记录（回收站 → 在用）。 */
  public Map<String, Object> restore(String entityCode, String id) {
    long tenantId = tenantProvider.currentTenantId();
    return runtimeRecordService.restore(entityCode, tenantId, id, currentOperator());
  }

  /**
   * 行级 ACL 断言（供关系端点复用：子表可见性由父行行级 ACL 控制）。
   *
   * <p>原 {@code RuntimeRelationController} 直注 {@code JdbcRuntimeRecordService} 仅为此方法——经本类暴露后，
   * 适配层不再持有任何 engine 持久化服务。
   */
  public void assertRowOwner(PublishedRuntimeEntity entity, long tenantId, String id) {
    runtimeRecordService.assertRowOwner(entity, tenantId, id, currentOperator());
  }
}
