package com.bone.claim.infrastructure.task;

import com.bone.claim.domain.model.task.AssigneeType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 归属主体可用性适配器（批 1 的 fail-closed 底线判定）。
 *
 * <p><b>本适配器只回答「目标是否具备被分配的基本形态」</b>：类型必须受控、ID 必须非空。 空目标必须判为不可用——否则规则表里一条导入不全的规则会给案件写上一个空的"已指派"归属，
 * 任务既不在任何人的待办里、也不在部门池里，等于凭空消失（比"没分配"更糟：没有告警、没有兜底）。
 *
 * <p><b>为什么不在本批接 IAM 在职状态</b>：跨模块实时校验需要 IAM 的可用性与降级策略 （IAM 抖动时是"全不可分配"还是"按上次快照"？），属批 2
 * 集成范围。这里<b>不做假实现</b>—— 与其返回"永远可用"却对外宣称已做在职校验，不如把边界显式写在端口与验证报告里。 未来接 IAM 时只需替换本类，求解逻辑（{@code
 * TaskAssigner}）零改动。
 */
@Slf4j
@Component
public class DefaultAssigneeEligibilityAdapter implements AssigneeEligibilityPort {

  @Override
  public boolean isEligible(AssigneeType type, String assigneeId) {
    if (type == null) {
      // 非法类型不静默当成 USER：错配到 USER 会让任务落到一个"叫这个名字的用户"的待办里。
      return false;
    }
    if (assigneeId == null || assigneeId.isBlank()) {
      return false;
    }
    return switch (type) {
      case USER, DEPT, ROLE -> true;
    };
  }
}
