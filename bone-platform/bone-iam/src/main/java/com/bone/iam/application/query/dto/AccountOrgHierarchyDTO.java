package com.bone.iam.application.query.dto;

import lombok.Data;

/**
 * 账户的组织层级视图：审批流「直属主管 / 部门负责人 / 向上 N 级」三个指派策略的唯一取数口径。
 *
 * <p><b>为什么单独出一个端点而不是塞进账号详情</b>：这是一条<b>服务间契约</b>（bone-metadata-server 的 {@code
 * OrgHierarchyProvider} 适配器按需调用），不是给人看的账号档案。放进详情会让每次查账号都多两次 部门/关系 join，且把「审批语义」泄漏进账号 CRUD
 * 的响应体。Camunda 的 {@code IdentityService}、飞书的 「获取用户上级」也同样是独立接口。
 *
 * <p><b>三个字段都允许为 null，且 null 是有意义的信息</b>：无上级（创始人/CEO）、无部门、部门未指定负责人。 引擎侧拿到 null
 * 会<b>挂起</b>实例并通知管理员（fail-closed），绝不跳过审批节点——所以这里必须如实返回 null，不能用空串或 0 掩盖。
 */
@Data
public class AccountOrgHierarchyDTO {

  /** 被查询的账户。 */
  private Long accountId;

  /** 所属部门；未归属时为 null。 */
  private Long deptId;

  /** 直属主管（{@code LINE_MANAGER} 策略的取数点）；无上级时为 null。 */
  private Long lineManagerId;

  /** 所属部门的负责人（{@code DEPT_HEAD} 策略的取数点）；无部门或部门未指定负责人时为 null。 */
  private Long deptHeadId;
}
