package com.bone.metadata.catalog.application.query.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 待授权权限项（发布 onboarding，P1）。
 *
 * <p>元数据发布流程<b>不感知 IAM 授权态</b>（跨服务，无授权查询通道），故本项回答的是 「发布后<b>需要</b>授哪些码」而非「还缺哪些码」——UI 文案据此表述为「需授权」清单，
 * 已授过的动作由用户自行忽略（或后续 IAM 侧补授权态查询再收敛）。
 */
@Data
@Builder
public class PendingAuthorizationDTO {

  /** 需授予的权限码：{@code runtime:{entityCode}:{actionCode}}（RuntimeAccessGuard §4.6 动作级细码） */
  private String code;

  /** 动作编码（如 {@code confirmPayout}） */
  private String actionCode;

  /** 动作中文名（如「确认打款」），供清单可读展示 */
  private String actionLabel;
}
