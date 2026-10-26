package com.bone.workflow.starter.spi;

import java.util.Optional;

/**
 * 组织层级查询 SPI（宿主接入点）。
 *
 * <p>审批流最常用的「直属主管 / 部门负责人 / 向上 N 级」指派策略，其数据来自组织目录（人事系统 / IAM）， 而 IAM
 * 在本仓是<b>独立部署的服务</b>、且当前版本没有「直属主管 / 部门负责人」字段——因此这些策略在缺省实现里 解析不出人，由引擎<b>挂起</b>（失败关闭，不放行）。
 *
 * <p>宿主（或未来的 IAM HTTP 网关适配器）实现本接口后，{@code LINE_MANAGER} / {@code DEPT_HEAD} / {@code LEVEL_UP}
 * 即可解析出人； 未提供时 {@code RecordFieldApproverResolver} 保持 fail-closed（返回空 → 引擎挂起），不影响「缺依赖就放行」的最严重事故形态。
 */
public interface OrgHierarchyProvider {

  /** 某账户的直接上级（直属主管）。无上级返回 {@link Optional#empty()}。 */
  Optional<String> lineManagerOf(String accountId);

  /** 某账户所属部门的负责人。无部门 / 无负责人返回 {@link Optional#empty()}。 */
  Optional<String> deptHeadOf(String accountId);

  /**
   * 某账户向上 {@code levels} 级的主管（{@code levels=1} 等同 {@link #lineManagerOf}）。越界（已到顶层仍要再向上）返回 {@link
   * Optional#empty()}。
   */
  Optional<String> levelUpOf(String accountId, int levels);
}
