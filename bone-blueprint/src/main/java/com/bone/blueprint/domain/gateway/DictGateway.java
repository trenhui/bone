package com.bone.blueprint.domain.gateway;

import java.util.List;
import java.util.Optional;

/**
 * 字典网关（领域出站端口）：消费 bone-system 的字典「下拉数据源」。
 *
 * <p><b>跨服务消费字典的唯一入口</b>：bone-system 字典模块设计明确把 {@code GET
 * /api/v1/system/dict/items/options?type={typeCode}} 定为全平台取字典的唯一 REST 契约（合并平台+租户覆盖、 过滤停用与未生效、按 lang
 * 本地化标签）。本端口把「字典长什么样、从哪取」隔离在领域之外， 领域层只表达业务问题：这个来源渠道码合法吗？它的中文名是什么？
 *
 * <p><b>降级约定（与 {@link MasterDataGateway} 同口径）</b>：字典服务不可达时返回 {@link Optional#empty()}，
 * 调用方按本地兜底继续——写路径放行（不阻断下单）、读路径标签置空，结果带 TTL 缓存避免抖动放大成下单失败。
 */
public interface DictGateway {

  /** 某值域全部（启用且生效）选项；字典服务不可达返回 empty。 */
  Optional<List<DictOptionView>> listOptions(String typeCode);

  /** 码 → 标签；码不存在或字典服务不可达返回 empty。 */
  Optional<String> resolveLabel(String typeCode, String code);

  /** 字典选项视图（领域层只认业务字段 code/label，不感知 system 存储形态）。 */
  record DictOptionView(String code, String label) {}
}
