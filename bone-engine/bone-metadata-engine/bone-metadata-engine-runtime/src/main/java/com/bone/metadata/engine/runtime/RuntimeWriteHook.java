package com.bone.metadata.engine.runtime;

/**
 * 写路径前置钩子 SPI（对齐 Directus Trigger 的 Filter 分层、Salesforce Trigger before 事件）。
 *
 * <p>通用校验器（{@link RuntimeWriteValidator}）承载「平台级规则」（required/type/validation_rules/ENUM）；
 * 本接口承载「实体特有业务规则」（如报销：审批通过后不允许改金额）。 二者分离的动机与「通用引擎不被 case 污染」一致： 平台级规则写死进引擎，实体级规则以 SPI
 * 挂在实体上，由业务模块按需注册。
 *
 * <p>钩子在写路径时序中的位置（Directus Filter 语义）：
 *
 * <ol>
 *   <li>{@code RuntimeWriteValidator} 平台级校验（所有实体强制）；
 *   <li>关系约束校验（{@code assertRelationConstraints}）；
 *   <li><strong>本钩子</strong>：实体级规则（可阻断——抛 {@link RuntimeRecordException}； 也可就地修改 {@code
 *       ctx.payload()} 以改写写入内容，与 Directus filter 可改载荷同构）；
 *   <li>审计列盖章 + 实际 INSERT/UPDATE。
 * </ol>
 *
 * <p>注册方式：实现本接口并声明为 Spring bean（同模块内自动装配进 {@code JdbcRuntimeRecordService}）；{@link #entityCode()}
 * 返回所属实体 code，未匹配实体不触发。
 */
public interface RuntimeWriteHook {

  /** 本钩子作用的运行时实体 code（如 {@code expense_claim}）。 */
  String entityCode();

  /**
   * 创建前钩子：可阻断（抛 {@link RuntimeRecordException}）或改写 {@code ctx.payload()}。
   *
   * @param ctx 含过滤后载荷（可写字段，不含审计列；此时主键/租户/软删默认值已注入）
   */
  default void beforeCreate(RuntimeWriteHookContext ctx) {
    // no-op
  }

  /**
   * 更新前钩子：可阻断或改写载荷。记录尚未锁版本，钩子内如需读当前行应经 {@code JdbcRuntimeRecordService#getById} 或自定义查询，勿直接依赖快照一致性。
   *
   * @param ctx 含过滤后载荷（不含审计列与主键）
   */
  default void beforeUpdate(RuntimeWriteHookContext ctx) {
    // no-op
  }
}
