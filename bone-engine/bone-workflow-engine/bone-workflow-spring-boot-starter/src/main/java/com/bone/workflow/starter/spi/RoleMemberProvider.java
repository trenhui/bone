package com.bone.workflow.starter.spi;

import java.util.List;

/**
 * 角色成员查询 SPI（宿主接入点）。
 *
 * <p><b>为什么是 SPI 而不是直接依赖 IAM 类</b>：全仓没有任何模块把 {@code bone-iam} 当 Maven 库消费——它是<b>独立部署的 服务</b>，只走
 * HTTP；且本地 {@code bone-iam:1.0.0} 产物的包结构与现行源码不一致（{@code com.bone.iam.domain.repository} 不存在）。把 IAM
 * 类引进桥接层只会得到一个编译不过、且锁死在旧 API 上的依赖。
 *
 * <p>宿主（或未来的 IAM HTTP 网关适配器）实现本接口即可让 {@code ROLE} 指派策略生效；未提供时 {@code ROLE} 解析不出人 →
 * 引擎<b>挂起</b>（失败关闭，不是放行）。
 */
public interface RoleMemberProvider {

  /**
   * 按角色码返回成员账户 id。
   *
   * @param roleCode 角色码
   * @return 成员账户 id 列表；角色不存在或无成员时返回空列表
   */
  List<String> accountIdsByRoleCode(String roleCode);
}
