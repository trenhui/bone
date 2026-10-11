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
}
