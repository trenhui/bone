package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import com.bone.metadata.runtime.application.RuntimeActionApplicationService;
import com.bone.metadata.runtime.application.RuntimeActionMetaApplicationService;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模式 B 运行时「动作」端点（META-002B 状态机动作 SPI，Runtime 数据面设计 §4.7）。
 *
 * <p>与 {@link com.bone.metadata.runtime.adapter.web.RuntimeRecordController} 同源：控制器只做参数绑定 + 租户解析 +
 * 鉴权，编排交给 {@code *ApplicationService}。动作触发是唯一合法的「状态流转」入口， 普通 CRUD 的 {@code PUT} 不再承担状态推进职责（呼应 G14）。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}")
@RequiredArgsConstructor
public class RuntimeActionController {

  private final RuntimeActionApplicationService actionService;
  private final RuntimeActionMetaApplicationService metaService;
  private final RuntimeAccessGuard accessGuard;
  private final TenantProvider tenantProvider;

  /**
   * 触发某条记录的某个动作（状态机驱动）。
   *
   * <p>请求体：{@code {"params": {...}}}（逃逸动作必填入参）； 资金类动作需携带 {@code Idempotency-Key} 请求头防重放； 建议携带
   * {@code If-Match} 与列表/详情返回的 ETag 对齐，避免基于陈旧快照推进状态。
   *
   * <p><b>注解层为何保持粗码 {@code metadata:runtime:write}</b>（其余运行时端点均用
   * {@code @runtimeAccessGuard.canXxx(#entityCode)} 动态判据）：动作级授权判据是 {@code canInvoke(def)}，其形状（逃逸要细码
   * / 非逃逸要实体写或细码）<strong>没有单一布尔简化形式</strong>；在注解层用 {@code canRead}/{@code canWrite}
   * 预判都会误伤「仅持有动作细码的独立资金授权面」（无实体读写的审批人）。故注解只表达「能进运行时写域」，动作级判定全部下沉 {@code
   * RuntimeActionApplicationService}（def 感知）。ArchitectureTest 注解口径门禁对本方法登记豁免。
   */
  @PostMapping("/records/{id}/actions/{actionCode}")
  // 注解层只表达「能进运行时写域」：旧粗码 metadata:runtime:write、旧全库码 metadata:write、
  // 新通配写 runtime:*:write（RuntimeAccessGuard §4.7 已明确通配写等价全库 CRUD）、平台管理员 platform.admin。
  // 真正的实体级 / 动作级（含逃逸细码）授权全部下沉 RuntimeActionApplicationService#requireAction，
  // 此处不得再加实体级判据（会误伤仅持动作细码的独立资金授权面）。
  @PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write', 'runtime:*:write', 'platform.admin')")
  public ApiResponse<Map<String, Object>> invoke(
      @PathVariable String entityCode,
      @PathVariable String id,
      @PathVariable String actionCode,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody(required = false) Map<String, Object> body) {
    long tenantId = tenantProvider.currentTenantId();
    // 动作授权统一在 RuntimeActionApplicationService 内做（def 感知，Runtime §4.6 门禁）：
    // 控制器不解析 def，无法区分逃逸/非逃逸——在控制器按「实体级写」预判会让
    // 逃逸动作的细码持有者（无实体写时的独立资金授权面）先被 403，判定顺序冲突。
    Map<String, Object> params =
        (body == null) ? Map.of() : (Map<String, Object>) body.getOrDefault("params", body);
    Map<String, Object> result =
        actionService.invokeAction(
            entityCode,
            tenantId,
            id,
            actionCode,
            params,
            currentOperator(),
            idempotencyKey,
            CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ApiResponse.success(result);
  }

  /**
   * 列出某实体可用动作：不带 status 返回全部（前端一次拉取按 from_status 分组）；带 status 返回该状态可触发的动作。
   *
   * <p>注解判据与记录读一致（{@code canRead}）：动作按钮挂在列表/详情页，「看得见单据」是触发动作的产品前提； 细粒度裁剪（含逃逸动作必须细码）由 {@code
   * describe} 按 {@code canInvoke} 完成，与本注解同源。
   */
  @GetMapping("/actions")
  @PreAuthorize("@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<List<RuntimeActionDef>> listActions(
      @PathVariable String entityCode, @RequestParam(required = false) String status) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    List<RuntimeActionDef> list =
        (status == null || status.isBlank())
            ? metaService.listByEntity(tenantId, entityCode)
            : metaService.listByStatus(tenantId, entityCode, status);
    return ApiResponse.success(list);
  }

  /** 从 Spring Security 上下文取当前操作者标识（与 RuntimeRecordController 同语义）。 */
  private static String currentOperator() {
    org.springframework.security.core.Authentication auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth
            instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return null;
    }
    Object details = auth.getDetails();
    if (details instanceof Number n) {
      return n.toString();
    }
    if (details instanceof String s && s.matches("\\d+")) {
      return s;
    }
    String name = auth.getName();
    if (name != null && name.matches("\\d+")) {
      return name;
    }
    return name;
  }
}
