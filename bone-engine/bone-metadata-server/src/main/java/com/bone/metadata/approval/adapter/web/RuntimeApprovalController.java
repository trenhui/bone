package com.bone.metadata.approval.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.runtime.application.RuntimeApprovalApplicationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心 REST 入站面（我的待办 / 我的已办 / 单据审批概况 / 干预）。
 *
 * <p><b>为什么不在 {@code com.bone.metadata.runtime.adapter.web}</b>：那一套端点的判据形态是「实体级读写」
 * （{@code @runtimeAccessGuard.canRead(#entityCode)}，由元数据实体驱动的动态授权）。审批端点的授权对象是
 * <b>流程任务本身</b>——可见性判据是「任务指派快照 == 当前办理人」，与任何实体码都无绑定关系；硬塞进 entityCode 形态只会把 「跨单据待办中心」拆成 N
 * 个单实体列表，丢失它存在的意义。故独立成 {@code approval} 界，自带判据。
 *
 * <p><b>授权口径</b>：读侧（待办 / 已办 / 概况）要 {@code metadata:runtime:read}；写侧（改派 / 催办 / 强制指派 / 终止）要 {@code
 * metadata:runtime:write}。更细的「是否本单当前审批人」由 {@link RuntimeApprovalApplicationService} 与引擎断言， 注解层只做粗筛（
 * HERE 不存在 def 感知的双分支形态，与 {@code RuntimeActionController.invoke} 豁免同理）。
 *
 * <p>控制器只做参数绑定与编排转发，业务规则全部下沉 application。
 */
@RestController
@RequestMapping("/api/v1/runtime/approval")
@RequiredArgsConstructor
public class RuntimeApprovalController {

  private final RuntimeApprovalApplicationService approvalService;

  /** 我的待办：跨单据、跨实体的当前待办任务列表（真源 {@code wf_task}，非单据字段过滤）。 */
  @GetMapping("/tasks/pending")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> pending(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(approvalService.listPendingTasks(page, size));
  }

  /**
   * 单据审批概况：实例投影 + 当前待办 + 完整审批轨迹（详情页审批时间线用）。
   *
   * <p>实体级授权复用运行时同一套动态判据（区别于上面跨实体验证的粗筛）——概况是「读某张具体单据」，判据应与记录读一致。
   */
  @GetMapping("/records/{entityCode}/{recordId}")
  @org.springframework.security.access.prepost.PreAuthorize(
      "@runtimeAccessGuard.canRead(#entityCode)")
  public ApiResponse<Map<String, Object>> describe(
      @PathVariable String entityCode, @PathVariable String recordId) {
    return ApiResponse.success(approvalService.describe(entityCode, recordId));
  }

  /**
   * 一批单据的审批摘要（列表「当前审批人」列）：阶段 + 当前节点 + 待办人（复数）。
   *
   * <p>刻意<b>不</b>复用 {@code /records/{entityCode}/{recordId}} 逐行调用：那样一页 20 行会把 20
   * 张单据的完整审批轨迹（含全部历史任务）都拉一遍，只为渲染一列。
   *
   * <p><b>为什么用 POST 而不是 GET + 逗号串</b>：实测 20 个雪花 id 的查询串约 480 字符，叠上认证与 Cookie 头后触发容器 {@code Request
   * header is too large}——返回的是容器 HTML 错误页， 前端只能看到「列表加载失败」。批量读把 id 放进 body，请求行长度与页大小解耦。
   *
   * <p>授权：注解层只做粗筛（与其他读端点同口径），实体级判据在应用服务里 {@code accessGuard.requireRead}——body 里的 entityCode 无法在
   * SpEL 里安全求值。
   */
  @PostMapping("/summaries")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<Map<String, Object>> summaries(
      @RequestBody(required = false) Map<String, Object> body) {
    String entityCode = body == null ? null : textOf(body.get("entityCode"));
    Object rawIds = body == null ? null : body.get("recordIds");
    List<String> ids =
        rawIds instanceof List<?> list
            ? list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList()
            : List.of();
    return ApiResponse.success(approvalService.summaries(entityCode, ids));
  }

  private static String textOf(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /** 我的已办：已表态任务（通过 / 驳回 / 已转交 / 已作废），按决策时间倒序。 */
  @GetMapping("/tasks/decided")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<PageResult<Map<String, Object>>> decided(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.success(approvalService.listDecidedTasks(page, size));
  }

  /**
   * 改派：待办转交他人（{"assigneeId":"...","reason":"..."}）。
   *
   * <p>引擎侧按 {@code PENDING} 条件更新——办理人可能正在点同意，先查后改会改出一个「已办结却被转交」的幽灵任务。
   */
  @PostMapping("/tasks/{taskId}/reassign")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> reassign(
      @PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.reassign(taskId, body));
  }

  /** 催办：{"comment":"..."}。只通知 + 留痕，不改任务状态。 */
  @PostMapping("/tasks/{taskId}/urge")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> urge(
      @PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.urge(taskId, body));
  }

  /**
   * 强制指派：打破「审批人解析不出 → 挂起」的死单（{"assigneeId":"...","nodeCode":null,"reason":"..."}）。
   *
   * <p>挂起状态下引擎<b>根本不建任务</b>，{@code reassign} 无任务可改派，本端点是唯一的人工续跑出口。
   */
  @PostMapping("/instances/{instanceId}/assign")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> assign(
      @PathVariable String instanceId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.assign(instanceId, body));
  }

  /** 强制终止：{"reason":"..."}。管理员对挂起死单 / 错提单据的不可逆处置。 */
  @PostMapping("/instances/{instanceId}/terminate")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> terminate(
      @PathVariable String instanceId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.terminate(instanceId, body));
  }

  /**
   * 转办：办理人把<b>自己的</b>待办转给他人（{"assigneeId":"...","comment":"..."}）。
   *
   * <p>写权限粗筛 + 引擎侧「操作人 == 当前办理人」细判（与改派的管理员语义区分：转办是自助，改派是干预）。
   */
  @PostMapping("/tasks/{taskId}/transfer")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> transfer(
      @PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.transfer(taskId, body));
  }

  /** 加签：拉人会办当前节点（{"assigneeId":"...","comment":"..."}），被加签人拿到同节点 PENDING。 */
  @PostMapping("/tasks/{taskId}/addSign")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> addSign(
      @PathVariable String taskId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.addSign(taskId, body));
  }

  /**
   * 恢复挂起实例：组织数据修复后重跑审批人解析（{"reason":"..."} 可选，审计留痕用）。
   *
   * <p>与 {@code assign} 的分工：assign = 直接注入办理人（人已知），resume = 按原指派规则重解析（数据已修）。解析仍不出人时引擎保持挂起。
   */
  @PostMapping("/instances/{instanceId}/resume")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<Map<String, Object>> resume(
      @PathVariable String instanceId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.success(approvalService.resume(instanceId, body));
  }

  /** 我的站内信（收件箱）：{@code unreadOnly=true} 只看未读。接收人恒为当前操作人，不接受入参指定他人。 */
  @GetMapping("/inbox")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<java.util.List<Map<String, Object>>> inbox(
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return ApiResponse.success(approvalService.inbox(unreadOnly));
  }

  /**
   * 问题单据列表：按阶段列举实例（{@code phase=SUSPENDED} 挂起死单 / {@code REJECTED} 驳回单），更新时间倒序。
   *
   * <p>只截断不计数：返回体 {@code truncated=true} 表示该阶段问题单不止这些（上限 200）——排障入口不是分页浏览。
   */
  @GetMapping("/instances")
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<Map<String, Object>> instances(
      @RequestParam String phase, @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.success(approvalService.instancesByPhase(phase, limit));
  }
}
