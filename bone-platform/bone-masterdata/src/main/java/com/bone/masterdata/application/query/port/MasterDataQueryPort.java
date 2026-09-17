package com.bone.masterdata.application.query.port;

import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.standard.vo.StandardFieldCode;
import java.util.Optional;

/**
 * 主数据读侧端口（E-13.3 *QueryPort）。
 *
 * <p>CommandHandler 内的唯一性/存在性校验走本端口；读侧 DSL（Criteria）只允许出现在 {@code infrastructure/query}
 * 的实现中，应用层与领域层均不直接依赖持久化 DSL。
 */
public interface MasterDataQueryPort {

  /** 按实体名称统计数量（创建主数据实体时唯一性校验）。 */
  long countEntityByName(MasterDataEntityName name);

  /** 按来源元数据实体 ID 查询已转换主数据实体的 ID（尚未转换则返回空）。 */
  Optional<Long> findEntityIdByMetaEntityId(Long metaEntityId);

  /** 按 (实体 ID, 字段名) 统计字段数量（创建字段时唯一性校验）。 */
  long countFieldByEntityIdAndName(Long masterDataEntityId, FieldName name);

  /** 按 (实体编码, 字段编码) 统计数据标准数量（创建数据标准时唯一性校验）。 */
  long countStandardByEntityCodeAndFieldCode(String entityCode, StandardFieldCode fieldCode);
}
