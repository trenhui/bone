package com.bone.claim.domain.port.out;

import com.bone.claim.domain.model.task.AssigneeType;

/**
 * 归属主体可用性判定端口（分配求解期的准入闸门）。
 *
 * <p><b>为什么需要这个端口而不是在规则表里硬判</b>：「这条规则指向的主体此刻是否还能接活」
 * 是随时间变化的外部事实（离职、部门撤并、角色停用），把它写进规则表等于让运营手工同步一份会腐化的副本。 端口让求解逻辑只依赖一个布尔判定，具体数据源（IAM／组织架构）由适配器决定。
 *
 * <p><b>本批（批 1）的适配器只做 fail-closed 底线判定</b>：目标为空或类型非法即不可用。 IAM 在职状态的实时校验属批 2
 * 的集成范围——<b>不做假实现</b>（不返回「永远可用」却又声称做了在职校验）， 该边界在验证报告中显式登记。
 */
public interface AssigneeEligibilityPort {

  /**
   * 该主体当前是否可承接任务。
   *
   * @param type 主体类型（不可为 null）
   * @param assigneeId 主体 ID
   * @return true 表示可分配
   */
  boolean isEligible(AssigneeType type, String assigneeId);
}
