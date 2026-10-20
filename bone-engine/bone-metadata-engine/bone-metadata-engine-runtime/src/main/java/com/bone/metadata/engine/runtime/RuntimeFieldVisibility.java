package com.bone.metadata.engine.runtime;

import java.util.List;

/**
 * 字段级角色可见性配置（UI 协议 {@code control.props.roles} 的服务端投影）。
 *
 * <p><b>语义</b>：{@code roles} 是<b>允许清单</b>——用户角色码与清单有任一交集才可见该字段； 清单为空表示「所有人可见」（与未配置等价，协议默认值）。
 *
 * <p><b>denyAll</b>：同一字段在多个表面配了<b>互斥</b>的允许清单（如 detail {@code [hr]} vs form {@code [admin]}）时，
 * 投影器取交集得空集——此时字段对<b>所有人</b>不可见。这是故意的 fail-closed：矛盾配置必须表现为
 * 「字段全局消失」这种响亮失败，让建模者立即修配置；若退化为「所有人可见」，矛盾就变成静默泄露。
 *
 * <p><b>为什么服务端必须再裁决一次</b>：前端按角色隐藏只是体验层，HTTP 响应体仍带明文。 真正的约束必须在数据面生效——否则换个客户端（ curl / 旧前端 / 导出
 * API）就能拿到全部字段。本记录由 {@code RuntimeFieldVisibilityProjector} 从发布态 ui-schema 提取， 经 {@link
 * PublishedRuntimeEntity#fieldVisibilities()} 进入运行期查询/写入路径。
 *
 * <p><b>与 tier 的关系</b>：协议还有 {@code props.tier}（订阅档位），但 JWT 不含档位 claim、服务端无订阅上下文， 故本期只落地 roles；tier
 * 的服務端裁剪待订阅上下文接入后同机位扩展（见方案文档「诚实边界」）。
 */
public record RuntimeFieldVisibility(
    /** 逻辑字段编码（机制 B 下与物理列名解耦，这里始终用 code） */
    String fieldCode,
    /** 允许可见的角色码集合；空集合 = 所有人可见（除非 {@code denyAll}） */
    List<String> roles,
    /** true = 对所有人不可见（矛盾配置的交集为空），优先于 {@code roles} 判定 */
    boolean denyAll) {

  /** 常规构造：null roles → 空集合；去除空白码。 */
  public RuntimeFieldVisibility(String fieldCode, List<String> roles) {
    this(fieldCode, roles, false);
  }

  /** 矛盾配置构造：交集为空 ⇒ 所有人不可见。 */
  public static RuntimeFieldVisibility denyAll(String fieldCode) {
    return new RuntimeFieldVisibility(fieldCode, List.of(), true);
  }

  /** 归一化：null roles → 空集合；去除空白码。 */
  public RuntimeFieldVisibility {
    if (fieldCode == null || fieldCode.isBlank()) {
      throw new IllegalArgumentException("RuntimeFieldVisibility.fieldCode 不能为空");
    }
    roles =
        roles == null ? List.of() : roles.stream().filter(r -> r != null && !r.isBlank()).toList();
    if (denyAll && !roles.isEmpty()) {
      throw new IllegalArgumentException("denyAll 与 roles 互斥：" + fieldCode);
    }
  }

  /** 是否真的构成限制（空允许清单且非 denyAll = 不限制，调用方应直接跳过不建索引）。 */
  public boolean restricts() {
    return denyAll || !roles.isEmpty();
  }
}
