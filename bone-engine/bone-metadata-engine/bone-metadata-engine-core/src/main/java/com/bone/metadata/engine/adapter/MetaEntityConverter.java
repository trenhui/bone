package com.bone.metadata.engine.adapter;

import com.bone.metadata.engine.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.adapter.po.MetaFieldPo;
import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.domain.metadata.SmartFieldMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 防腐层（ACL）转换器：将 SDK 读取的 {@code meta_entity}/{@code meta_field} PO 转换为引擎领域模型。
 *
 * <p>采用<b>最小字段映射</b>策略：仅映射 server 侧 {@code MetaEntity}/{@code MetaField} 实际存在的字段， 引擎特有的扩展属性（AI
 * 自动填充、计算字段、虚拟字段、动态显示等）保留 {@code SmartFieldMetadata} 默认值， 不在此处强行对齐。后续 T2 领域模型归一时可在此补全。
 *
 * <p>本类位于 {@code adapter} 层，是端口与领域之间的唯一转换点，领域层不感知 SDK PO。
 */
public final class MetaEntityConverter {

  private MetaEntityConverter() {}

  /**
   * 将一个实体 PO 与其字段 PO 列表转换为引擎 {@link EntityMetadata}。
   *
   * @param entityPo 实体 PO（非 null）
   * @param fieldPos 该实体的字段 PO 列表（可为空）
   * @return 引擎实体元数据
   */
  public static EntityMetadata toEntityMetadata(MetaEntityPo entityPo, List<MetaFieldPo> fieldPos) {
    EntityMetadata entity = new EntityMetadata();
    entity.setId(String.valueOf(entityPo.getId()));
    entity.setName(entityPo.getName());
    // server 的 code 对应引擎的 apiName
    entity.setApiName(entityPo.getCode());
    entity.setLabel(entityPo.getDisplayName());
    entity.setDescription(entityPo.getDescription());
    entity.setTableName(entityPo.getTableName());
    entity.setActive(entityPo.getStatus() != null && entityPo.getStatus() == 2);
    entity.setSystem(Boolean.TRUE.equals(entityPo.getBuiltin()));

    Map<String, SmartFieldMetadata> fields = new LinkedHashMap<>();
    if (fieldPos != null) {
      for (MetaFieldPo fp : fieldPos) {
        SmartFieldMetadata f = new SmartFieldMetadata();
        f.setApiName(fp.getCode());
        f.setFieldName(fp.getName());
        f.setLabel(fp.getDisplayName());
        f.setType(fp.getType());
        f.setRequired(Boolean.TRUE.equals(fp.getRequired()));
        f.setUnique(Boolean.TRUE.equals(fp.getUnique()));
        f.setPrimaryKey(Boolean.TRUE.equals(fp.getPk()));
        f.setIndexed(Boolean.TRUE.equals(fp.getIndexed()));
        f.setLength(fp.getLength() == null ? 255 : fp.getLength());
        f.setDefaultValue(fp.getDefaultValue());
        f.setDescription(fp.getComment());
        fields.put(f.getApiName(), f);
      }
    }
    entity.setFields(fields);
    return entity;
  }
}
