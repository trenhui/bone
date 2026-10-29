package com.bone.workflow.starter.designer.web;

import com.bone.workflow.starter.designer.WorkflowDefinitionConflictException;
import com.bone.workflow.starter.designer.WorkflowDefinitionInvalidException;
import com.bone.workflow.starter.designer.WorkflowDefinitionRequestException;
import com.bone.workflow.starter.designer.WorkflowDesignerAccessDeniedException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 设计器 REST 异常 → HTTP 状态码 + {@link ApiResponse} 信封。
 *
 * <p>与前端契约：HTTP 4xx/5xx 时前端拦截器读取 {@code response.data.message} 作提示文案，读取 {@code
 * response.data.data.errorCode} 作结构化业务码。故 422 把违规清单放进 {@code data.violations}，前端据此逐条高亮到画布节点——
 * 而不是把「哪个节点缺什么」压成一句话让用户回去猜。
 *
 * <p>映射口径（与异常类注释一致）：
 *
 * <ul>
 *   <li>{@link WorkflowDefinitionInvalidException} → <b>422</b>（定义本身配错，fail-closed 拒绝发布）
 *   <li>{@link WorkflowDefinitionConflictException} → <b>409</b>（revision 过期 / 并发首存 / 版本号撞车）
 *   <li>{@link WorkflowDesignerAccessDeniedException} → <b>403</b>（授权实现拒绝；{@code data.action}
 *       给出被拒的动作档位， 便于前端区分「找管理员授权」与「改输入重试」）
 *   <li>{@link WorkflowDefinitionRequestException} → <b>400</b>（请求形态问题：JSON 不可解析 / key 不一致 / 缺操作人 /
 *       原文超长）
 *   <li>{@link IllegalStateException} → <b>400</b>（缺租户上下文等宿主装配问题）
 * </ul>
 *
 * <p><b>为什么必须显式 {@code @Order(HIGHEST_PRECEDENCE)} 且限定 {@code basePackages}</b>（2026-10-28
 * 联调实测确诊）：{@code @ControllerAdvice} 缺省顺序是 {@code LOWEST_PRECEDENCE}，且 Spring 解析异常时 <b>按 advice
 * 顺序逐个找第一个能处理该异常的方法</b>——它不会跨 advice 比较「谁的异常类型更具体」。 宿主（bone-metadata-server）自带一个带
 * {@code @ExceptionHandler(Exception.class)} 的 {@code GlobalExceptionHandler}，两者同级时按 bean
 * 名排序，{@code globalExceptionHandler} 在前 ⇒ 本类的 409/422/400 映射<b>一行都没生效</b>，前端看到的是清一色 500： 「覆盖草稿必须携带
 * revision」被渲染成「系统内部错误」，用户只会去重试而不是刷新版本。
 *
 * <p>两个注解缺一不可：
 *
 * <ul>
 *   <li>{@code @Order(HIGHEST_PRECEDENCE)} —— 让本类先于任何宿主 catch-all 被查找；
 *   <li>{@code basePackages} —— 本类对 {@link IllegalStateException} 的 400 映射是<b>宽口径</b>的，
 *       若全局生效，宿主任意位置的编程错误（同样是 IllegalStateException）都会被伪装成 400「请求有误」。
 *       限定到设计器控制器所在包后，该宽口径只覆盖设计器调用链，其他模块的 5xx 语义不受影响。
 * </ul>
 */
@ControllerAdvice(basePackages = "com.bone.workflow.starter.designer.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowDesignerExceptionAdvice {

  @ExceptionHandler(WorkflowDesignerAccessDeniedException.class)
  public ResponseEntity<ApiResponse<Object>> handleAccessDenied(
      WorkflowDesignerAccessDeniedException ex) {
    return ResponseEntity.status(403)
        .body(
            ApiResponse.of(
                403,
                ex.getMessage(),
                Map.of(
                    "action",
                    ex.action().name(),
                    "defKey",
                    ex.defKey() == null ? "" : ex.defKey())));
  }

  @ExceptionHandler(WorkflowDefinitionInvalidException.class)
  public ResponseEntity<ApiResponse<Object>> handleInvalid(WorkflowDefinitionInvalidException ex) {
    List<?> violations = ex.violations();
    return ResponseEntity.status(422)
        .body(ApiResponse.of(422, ex.getMessage(), Map.of("violations", violations)));
  }

  @ExceptionHandler(WorkflowDefinitionConflictException.class)
  public ResponseEntity<ApiResponse<Object>> handleConflict(
      WorkflowDefinitionConflictException ex) {
    return ResponseEntity.status(409).body(ApiResponse.of(409, ex.getMessage(), null));
  }

  @ExceptionHandler(WorkflowDefinitionRequestException.class)
  public ResponseEntity<ApiResponse<Object>> handleRequest(WorkflowDefinitionRequestException ex) {
    return ResponseEntity.status(400).body(ApiResponse.of(400, ex.getMessage(), null));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
    return ResponseEntity.status(400).body(ApiResponse.of(400, ex.getMessage(), null));
  }
}
