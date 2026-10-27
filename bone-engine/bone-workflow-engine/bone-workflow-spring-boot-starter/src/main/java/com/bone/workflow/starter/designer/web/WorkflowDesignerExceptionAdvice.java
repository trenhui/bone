package com.bone.workflow.starter.designer.web;

import com.bone.workflow.starter.designer.WorkflowDefinitionConflictException;
import com.bone.workflow.starter.designer.WorkflowDefinitionInvalidException;
import com.bone.workflow.starter.designer.WorkflowDefinitionRequestException;
import java.util.List;
import java.util.Map;
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
 *   <li>{@link WorkflowDefinitionRequestException} → <b>400</b>（请求形态问题：JSON 不可解析 / key 不一致 / 缺操作人）
 *   <li>{@link IllegalStateException} → <b>400</b>（缺租户上下文等宿主装配问题）
 * </ul>
 */
@ControllerAdvice
public class WorkflowDesignerExceptionAdvice {

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
