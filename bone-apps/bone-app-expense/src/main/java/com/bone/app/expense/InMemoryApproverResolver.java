package com.bone.app.expense;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MVP 审批人解析（占位）：角色→账户 id 的静态映射，仅用于打通自动派单链路与本地联调。
 *
 * <p><b>生产替换</b>：实现 {@link ExpenseApprovalRouter.ApproverResolver} 接入组织目录 （按申请人解析其直属主管 / 部门负责人 /
 * 财务负责人），不要在代码里硬编码账户 id。
 */
@Component
public class InMemoryApproverResolver implements ExpenseApprovalRouter.ApproverResolver {

  // TODO(L3 配置化): 改为从组织目录 / 配置实体读取，勿硬编码账户 id。
  private static final Map<String, String> ROLE_TO_ACCOUNT =
      Map.of(
          "LINE_MANAGER", "acc_line_manager",
          "DEPT_HEAD", "acc_dept_head",
          "FINANCE_HEAD", "acc_finance_head");

  @Override
  public String resolve(String roleCode, ExpenseApprovalRouter.Band band) {
    return ROLE_TO_ACCOUNT.get(roleCode);
  }
}
