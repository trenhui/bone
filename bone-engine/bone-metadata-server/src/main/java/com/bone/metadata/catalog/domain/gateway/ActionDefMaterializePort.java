package com.bone.metadata.catalog.domain.gateway;

import com.bone.metadata.catalog.domain.model.template.MetaModelTemplateAction;
import java.util.List;

/**
 * 「把模板动作定义展开成实体动作定义」的写出端口（G3 扩展 · 迁移 0040）。
 *
 * <p><b>为什么单独成端口</b>：实例化属于 catalog 界，而 {@code meta_entity_action_def} 的写入此前不存在 （{@code
 * JdbcRuntimeActionDefRepository} 只读）。与其把写入塞进 runtime 界的读仓储， 不如由 catalog 声明自己需要的能力，实现落在 {@code
 * infrastructure/gateway}—— 依赖方向保持 application → domain ← infrastructure。
 *
 * <p><b>契约</b>：整体在一个事务内展开；已存在同 {@code action_code} 的定义按 UPSERT 更新， 使重复实例化（同实体）幂等，不会因唯一键冲突让整个实例化失败。
 */
public interface ActionDefMaterializePort {

  /**
   * 将一组模板动作按目标实体展开写入 {@code meta_entity_action_def}。
   *
   * @param tenantId 目标租户
   * @param entityCode 实例化产出的实体 code（动作码命名空间随之确定）
   * @param actions 模板动作定义（按 sort_order 有序）
   * @return 本次写入（含 UPSERT）的行动定义条数
   */
  int materialize(long tenantId, String entityCode, List<MetaModelTemplateAction> actions);

  /**
   * 软删某实体在 {@code meta_entity_action_def} 下的全部动作定义（实体删除时级联）。
   *
   * <p><b>为什么必须有</b>：实体删除此前只删 {@code meta_entity} 一行，动作定义原样存活。后果有两个—— ① 运行时仍向一个已不存在的实体暴露动作入口（幽灵按钮 /
   * 幽灵 API）； ② 重建同名实体时 UPSERT 会把旧定义复活，用户看到「没配过却有动作」。二者都是数据正确性问题而非整洁性问题。
   *
   * <p>软删而非物理删：与 {@code meta_entity} 的逻辑删除同口径，保留审计线索；唯一键 {@code uk_action_tenant_entity_code} 由
   * {@code deleted} 不参与约束，故不影响同名实体重建。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code（须传<b>改写前</b>的原始值：草稿删除会追加 {@code _del_<id>} 后缀）
   * @return 本次软删的定义条数
   */
  int deleteByEntity(long tenantId, String entityCode);

  /**
   * 查询某实体的<b>逃逸动作</b>（资金/外部副作用类）清单，按 {@code sort_order} 升序（发布 onboarding 用）。
   *
   * <p><b>为什么读也落本端口</b>：与 {@link #materialize} / {@link #deleteByEntity} 同一张表（{@code
   * meta_entity_action_def}）的 catalog 侧能力声明——catalog 不反向依赖 runtime 界的读仓储（{@code
   * RuntimeActionDefRepository}），与 0040 建端口时的边界理由一致。
   *
   * <p><b>消费方</b>：发布预览的「待授权权限」清单。逃逸动作必须命中动作级细码 {@code runtime:{entityCode}:{actionCode}}
   * （RuntimeAccessGuard §4.6，实体级/通配 write 均不覆盖），而发布流程不感知 IAM 授权态——本清单回答的是「发布后<b>需要</b>授哪些码」，
   * 让建模者发布时就看到补权清单，而不是等用户点了置灰按钮再来问。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code
   * @return 逃逸动作快照（空列表 = 无逃逸动作，如主数据类实体）
   */
  List<ActionDefSnapshot> listEscapeActions(long tenantId, String entityCode);

  /**
   * 列出某实体全部动作定义（建模工作台「状态机」面板的数据源），按 {@code sort_order, id} 升序。
   *
   * <p>与 {@link #listEscapeActions} 同表同端口（catalog 不反向依赖 runtime 读仓储），只是不做逃逸过滤。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code
   * @return 动作定义列表（空 = 该实体未配状态机，运行时 {@code stateField} 恒 null）
   */
  List<ActionDefSpec> listByEntity(long tenantId, String entityCode);

  /**
   * 按 {@code actionCode} UPSERT 单条动作定义（同名即更新）。
   *
   * <p>用 {@code uk_action_tenant_entity_code} 判重而非先查后写：并发下「查不到→插入」会撞唯一键，UPSERT 让重复提交退化为更新，与 {@link
   * #materialize} 语义一致（重复实例化不中断流程）。
   *
   * <p>{@code permission_code} 由实现按 {@code runtime:{entityCode}:{actionCode}}
   * 推导写入，不接受调用方传入——放开会让细码与动作码脱钩， 逃逸动作因此永远命中不了授权。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code（权限码命名空间）
   * @param spec 动作定义
   * @return 1（UPSERT 恒写一行）
   */
  int upsert(long tenantId, String entityCode, ActionDefSpec spec);

  /**
   * 软删单条动作定义。
   *
   * <p>软删而非物理删：与 {@code meta_entity} 同口径保留审计线索；{@code uk_action_tenant_entity_code} 不含 {@code
   * deleted}， 故同名动作可被 {@link #upsert} 复活。
   *
   * @param tenantId 租户
   * @param entityCode 实体 code
   * @param actionCode 动作编码
   * @return 实际软删行数（0 = 该动作不存在或已软删，调用方据此判 404）
   */
  int softDeleteOne(long tenantId, String entityCode, String actionCode);
}
