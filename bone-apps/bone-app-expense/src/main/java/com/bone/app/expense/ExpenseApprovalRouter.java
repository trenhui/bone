package com.bone.app.expense;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 报销金额分级审批路由（G2）：按申报金额映射到审批角色，再经 {@link ApproverResolver} 解析为具体审批人。
 *
 * <p>口径对齐费用报销建模方案 §1.7 路由表：
 *
 * <ul>
 *   <li>≤ ¥1000 → 直属主管（LINE_MANAGER）
 *   <li>¥1000 &lt; x ≤ ¥5000 → 部门负责人（DEPT_HEAD）
 *   <li>&gt; ¥5000 → 财务负责人（FINANCE_HEAD），并标记会签（countersign），会签完成判定属 G2-v1.1
 * </ul>
 *
 * <p>本类只负责「金额→角色/决议」的纯逻辑，不持有组织目录；具体账户解析交给可替换的 {@link ApproverResolver} （MVP 用 {@code
 * InMemoryApproverResolver}，生产应替换为接组织目录的实现）。
 */
@Component
public class ExpenseApprovalRouter {

  /** 审批金额档。 */
  public enum Band {
    LOW,
    MID,
    HIGH
  }

  /** 路由决议（不可变）。 */
  public record RouteDecision(Band band, String roleCode, String approverId, boolean countersign) {}

  /** 审批人解析 seam：角色 / 金额档 → 具体账户 id。生产接组织目录（按申请人解析其直属主管 / 部门负责人 / 财务负责人）， 不要在代码里硬编码账户 id。 */
  public interface ApproverResolver {
    String resolve(String roleCode, Band band);
  }

  private final ApproverResolver resolver;

  public ExpenseApprovalRouter(ApproverResolver resolver) {
    this.resolver = resolver;
  }

  public RouteDecision route(BigDecimal amount) {
    Band band = bandOf(amount);
    String roleCode =
        switch (band) {
          case LOW -> "LINE_MANAGER";
          case MID -> "DEPT_HEAD";
          case HIGH -> "FINANCE_HEAD";
        };
    String approverId = resolver.resolve(roleCode, band);
    return new RouteDecision(band, roleCode, approverId, band == Band.HIGH);
  }

  /** 金额档划分（边界：≤1000 归 LOW，≤5000 归 MID，其余 HIGH）。 */
  public static Band bandOf(BigDecimal amount) {
    if (amount.compareTo(new BigDecimal("1000")) <= 0) {
      return Band.LOW;
    }
    if (amount.compareTo(new BigDecimal("5000")) <= 0) {
      return Band.MID;
    }
    return Band.HIGH;
  }
}
