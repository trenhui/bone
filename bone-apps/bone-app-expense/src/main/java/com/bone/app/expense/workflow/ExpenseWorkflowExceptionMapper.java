package com.bone.app.expense.workflow;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;

/**
 * 引擎异常 → 平台稳定错误码的映射（单一真源）。
 *
 * <p>纪律（引擎 README「错误分类」）：宿主按 {@link WorkflowErrorCode} 的 {@code httpStatus()} 语义映射，<b>不解析异常
 * message</b>—— 否则同一条语义在引擎与平台两处各写一套字符串，迟早漂移。本类是报销场景的唯一映射点：
 *
 * <ul>
 *   <li>{@code NOT_ASSIGNEE} / {@code NOT_INITIATOR}（403）→ {@code RUNTIME_FORBIDDEN_ACTION}；
 *   <li>{@code CONFLICT} / {@code ALREADY_DECIDED} / {@code ALREADY_STARTED} / {@code
 *       INSTANCE_TERMINATED}（409）→ {@code RUNTIME_ACTION_ILLEGAL_TRANSITION}（同族语义：当前状态不允许该操作）；
 *   <li>其余（422：定义缺失/非法、实例/任务不存在、无审批人挂起、分支异常）→ {@code RUNTIME_VALIDATION_FAILED}。
 * </ul>
 */
final class ExpenseWorkflowExceptionMapper {

  private ExpenseWorkflowExceptionMapper() {}

  static RuntimeRecordException translate(WorkflowEngineException ex) {
    WorkflowErrorCode code = ex.code();
    String message = ex.getMessage();
    return switch (code) {
      case NOT_ASSIGNEE, NOT_INITIATOR -> new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION, message);
      case CONFLICT,
          ALREADY_DECIDED,
          ALREADY_STARTED,
          INSTANCE_TERMINATED -> new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION, message);
      default -> new RuntimeRecordException(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, message);
    };
  }
}
