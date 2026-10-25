package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpenseWorkflowExceptionMapper}：引擎错误分类 → 平台稳定错误码映射（单一真源，按 {@code code()} 判、不解析 message）。
 */
class ExpenseWorkflowExceptionMapperTest {

  @Test
  @DisplayName("NOT_ASSIGNEE / NOT_INITIATOR → 403 RUNTIME_FORBIDDEN_ACTION")
  void forbiddenCodes_mapTo403() {
    for (WorkflowErrorCode code :
        new WorkflowErrorCode[] {WorkflowErrorCode.NOT_ASSIGNEE, WorkflowErrorCode.NOT_INITIATOR}) {
      RuntimeRecordException ex =
          ExpenseWorkflowExceptionMapper.translate(new WorkflowEngineException(code, "m"));
      assertThat(ex.getErrorCode()).isEqualTo(MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION);
    }
  }

  @Test
  @DisplayName(
      "CONFLICT / ALREADY_DECIDED / ALREADY_STARTED / INSTANCE_TERMINATED → 409 RUNTIME_ACTION_ILLEGAL_TRANSITION")
  void conflictCodes_mapTo409() {
    for (WorkflowErrorCode code :
        new WorkflowErrorCode[] {
          WorkflowErrorCode.CONFLICT,
          WorkflowErrorCode.ALREADY_DECIDED,
          WorkflowErrorCode.ALREADY_STARTED,
          WorkflowErrorCode.INSTANCE_TERMINATED
        }) {
      RuntimeRecordException ex =
          ExpenseWorkflowExceptionMapper.translate(new WorkflowEngineException(code, "m"));
      assertThat(ex.getErrorCode()).isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION);
    }
  }

  @Test
  @DisplayName(
      "DEFINITION_NOT_FOUND / DEFINITION_INVALID / INSTANCE_NOT_FOUND / TASK_NOT_FOUND / NO_ASSIGNEE / BRANCH_* → 400 RUNTIME_VALIDATION_FAILED")
  void remainingCodes_mapTo400() {
    for (WorkflowErrorCode code :
        new WorkflowErrorCode[] {
          WorkflowErrorCode.DEFINITION_NOT_FOUND,
          WorkflowErrorCode.DEFINITION_INVALID,
          WorkflowErrorCode.DEFINITION_MALFORMED,
          WorkflowErrorCode.INSTANCE_NOT_FOUND,
          WorkflowErrorCode.TASK_NOT_FOUND,
          WorkflowErrorCode.NO_ASSIGNEE,
          WorkflowErrorCode.BRANCH_AMBIGUOUS,
          WorkflowErrorCode.BRANCH_INVALID,
          WorkflowErrorCode.BRANCH_NO_MATCH
        }) {
      RuntimeRecordException ex =
          ExpenseWorkflowExceptionMapper.translate(new WorkflowEngineException(code, "m"));
      assertThat(ex.getErrorCode()).isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED);
    }
  }

  @Test
  @DisplayName("原始 message 原样透传（可诊断性不因映射丢失）")
  void message_preserved() {
    RuntimeRecordException ex =
        ExpenseWorkflowExceptionMapper.translate(
            new WorkflowEngineException(WorkflowErrorCode.DEFINITION_NOT_FOUND, "未找到已发布的流程定义"));

    assertThat(ex.getMessage()).isEqualTo("未找到已发布的流程定义");
  }

  @Test
  @DisplayName("全错误码覆盖（WorkflowErrorCode 新增码时必须显式归类，防止静默落 default）")
  void allErrorCodes_areCovered() {
    for (WorkflowErrorCode code : WorkflowErrorCode.values()) {
      RuntimeRecordException ex =
          ExpenseWorkflowExceptionMapper.translate(new WorkflowEngineException(code, "m"));
      assertThat(ex.getErrorCode())
          .as("错误码 %s 必须被显式映射", code)
          .isIn(
              MetadataErrorCodes.RUNTIME_FORBIDDEN_ACTION,
              MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION,
              MetadataErrorCodes.RUNTIME_VALIDATION_FAILED);
    }
  }
}
