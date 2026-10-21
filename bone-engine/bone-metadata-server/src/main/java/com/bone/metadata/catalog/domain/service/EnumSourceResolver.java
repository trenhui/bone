package com.bone.metadata.catalog.domain.service;

import com.bone.metadata.catalog.domain.model.meta.OptionItem;
import java.util.List;
import java.util.Optional;

/**
 * 枚举选项源解析端口（doc/design/modules/11 §10）：按选项源声明解析真源选项列表。
 *
 * <p><b>为什么是端口而不是直连</b>：字典的真源在 bone-system（{@code sys_dict_*}），跨服务直读会把对方的表结构 泄漏进 catalog（对方一改模型
 * catalog 就炸裂）。本端口把「选项从哪来、长什么样」隔离在领域之外，领域只表达 业务问题：这个值域此刻有哪些生效选项。实现（HTTP ACL 适配器）落在 {@code
 * infrastructure/enumsource}。
 *
 * <p><b>P0 范围</b>：仅 {@code dict} 类型（bone-system 下拉数据源）。{@code reference}（主数据参考值域）与 {@code
 * external}（外部服务连接器）按 doc 11 分期在 P1/P2 接入，届时以同端口新增解析方法的方式扩展， 不改变调用方形态。
 *
 * <p><b>降级约定</b>（与 bone-blueprint {@code DictGateway} 同口径）：真源不可达时返回 {@link Optional#empty()}，
 * 由调用方决定处置——写路径（保存/发布物化）fail-closed 抛错，读路径（预览/漂移对比）fail-soft 回落已物化快照。
 */
public interface EnumSourceResolver {

  /**
   * 解析某字典值域的全部生效选项（带 TTL 缓存，供被动读路径用）。
   *
   * <p>平台 + 租户覆盖合并、停用/未生效过滤、按 lang 本地化均由 bone-system {@code /options} 内部完成， catalog
   * 侧无感——「唯一取数契约」不裂变。
   *
   * @param typeCode 值域编码（{@code sys_dict_type.code}）
   * @param language 语言标签（如 zh-CN）；为空用默认 label
   * @return 选项列表；真源不可达或值域不存在返回 {@link Optional#empty()}
   */
  Optional<List<OptionItem>> resolveDictOptions(String typeCode, String language);

  /**
   * 强制穿透缓存解析（写路径与用户触发的刷新用）。
   *
   * <p><b>为什么需要强制档</b>（E2E 实证的设计修正）：TTL 缓存对被动读（预览/漂移徽标）是合理的权衡， 但用在两类调用上是错的——①
   * 保存/发布物化：缓存旧数据会把「发布即冻结」冻结成旧口径，发布后漂移 立刻报警；② 用户点「刷新」：点了却拿到 5 分钟前的数据，动作失去意义。这两类调用必须拿到真源此刻 的状态，宁可多一次
   * HTTP。
   *
   * @param typeCode 值域编码
   * @param language 语言标签；为空用默认 label
   * @return 选项列表；真源不可达或值域不存在返回 {@link Optional#empty()}
   */
  Optional<List<OptionItem>> resolveDictOptionsFresh(String typeCode, String language);

  /**
   * 值域业务版本号（v1.1 版本协商 / SWR，doc/design/lowcode/ui/界面设计器字典打通设计方案.md §4.3）。
   *
   * <p>真源（bone-system）任何内容变更都会使该版本号单调递增（epoch millis）。resolver 用它在读路径做
   * 「版本比对」：版本未变直接返缓存（分钟内零字典服务调用），版本变了才实时重取—— 既把字典服务调用量降下来，又保证漂移在变更后立即可见，不回归 T8b/T8c 漂移实时可见的实证。
   *
   * @param typeCode 值域编码
   * @return 版本号；值域不存在或真源不可达返回 {@link Optional#empty()}
   */
  Optional<Long> resolveDictVersion(String typeCode);

  /**
   * 选项视图（领域层只认业务字段 code/label/state，不感知 bone-system 的存储与传输形态）。
   *
   * <p><b>三态生命周期</b>（界面设计器字典打通 v1.1 §4.5）：{@code state} ∈ ACTIVE / DEPRECATED（DISABLED 不进 {@code
   * /options}）。物化把 DEPRECATED 一并写进快照（存量写放行），渲染侧按 ACTIVE 过滤（新表单不出现）。 存量快照与 inline 枚举无 state 字段，按
   * ACTIVE 兼容。
   */
}
