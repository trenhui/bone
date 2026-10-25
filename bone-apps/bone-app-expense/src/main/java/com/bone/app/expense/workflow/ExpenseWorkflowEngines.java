package com.bone.app.expense.workflow;

import com.bone.workflow.domain.WorkflowEngine;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 引擎引用的惰性解析（三个逃逸 Handler 共用）。
 *
 * <p><b>为什么不构造器直注入</b>：引擎本体由 starter 的 {@code WorkflowEngineAutoConfiguration} 装配（全端口就位时）。 直注入意味着
 * 「端口缺失 ⇒ 引擎不装配 ⇒ 报销模块把宿主上下文打挂」——与 starter「缺端口不是引擎的错，不打挂宿主启动」的哲学相悖。 惰性解析把缺失
 * 推迟到<b>动作执行时</b>：那一刻明确失败（500 + 可诊断文案），而不是让整个应用起不来。
 *
 * <p>bone-boot 组装面内七个端口恒在位（starter 随本模块依赖入场），故本分支在目标部署形态下不会触发； 它是给「模块被装进一个没接引擎的
 * 宿主」留的诚实失败，而不是静默跳过审批。
 */
final class ExpenseWorkflowEngines {

  private ExpenseWorkflowEngines() {}

  static WorkflowEngine require(ObjectProvider<WorkflowEngine> provider) {
    WorkflowEngine engine = provider.getIfAvailable();
    if (engine == null) {
      throw new IllegalStateException(
          "审批流引擎未装配（bone-workflow-spring-boot-starter 端口缺失）——报销审批流不可用，请联系平台管理员");
    }
    return engine;
  }
}
