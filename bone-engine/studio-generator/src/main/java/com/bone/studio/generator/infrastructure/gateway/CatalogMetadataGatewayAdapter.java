package com.bone.studio.generator.infrastructure.gateway;

import com.bone.studio.generator.domain.catalog.model.CatalogMetaEntity;
import com.bone.studio.generator.domain.catalog.model.CatalogMetaField;
import com.bone.studio.generator.domain.catalog.repository.CatalogMetaEntityRepository;
import com.bone.studio.generator.domain.catalog.repository.CatalogMetaFieldRepository;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.TableColumn;
import com.bone.studio.generator.domain.gateway.CatalogMetadataGateway;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 已发布实体 status=1 */
@Component
@RequiredArgsConstructor
public class CatalogMetadataGatewayAdapter implements CatalogMetadataGateway {

  private static final int PUBLISHED = 1;

  /** 0-GENERATIVE：参与标准 CRUD 代码生成；1-RUNTIME 走 engine 动态 API */
  private static final int DELIVERY_RUNTIME = 1;

  private final CatalogMetaEntityRepository catalogMetaEntityRepository;
  private final CatalogMetaFieldRepository catalogMetaFieldRepository;

  @Override
  public List<DatabaseTable> loadPublishedSnapshots(Long tenantId, List<String> entityCodes) {
    long tid = tenantId != null ? tenantId : 1L;
    var query =
        catalogMetaEntityRepository
            .query()
            .where(CatalogMetaEntity::getTenantId)
            .eq(tid)
            .and(CatalogMetaEntity::getStatus)
            .eq(PUBLISHED);

    List<CatalogMetaEntity> entities = query.orderByAsc(CatalogMetaEntity::getCode).list();

    Set<String> codeFilter = entityCodes == null ? Set.of() : new HashSet<>(entityCodes);
    List<DatabaseTable> tables = new ArrayList<>();
    for (CatalogMetaEntity entity : entities) {
      if (!codeFilter.isEmpty() && !codeFilter.contains(entity.getCode())) {
        continue;
      }
      if (entity.getDeliveryMode() != null && entity.getDeliveryMode() == DELIVERY_RUNTIME) {
        continue;
      }
      tables.add(toDatabaseTable(entity));
    }
    return tables;
  }

  private DatabaseTable toDatabaseTable(CatalogMetaEntity entity) {
    List<CatalogMetaField> fields =
        catalogMetaFieldRepository
            .query()
            .where(CatalogMetaField::getEntityId)
            .eq(entity.getId())
            .orderByAsc(CatalogMetaField::getSortOrder)
            .list();

    List<TableColumn> columns = new ArrayList<>();
    for (CatalogMetaField f : fields) {
      columns.add(
          TableColumn.builder()
              .columnName(f.getCode())
              .columnType(f.getType())
              .columnComment(
                  StringUtils.hasText(f.getDisplayName()) ? f.getDisplayName() : f.getName())
              .nullable(f.getRequired() == null || !f.getRequired())
              .primaryKey(Boolean.TRUE.equals(f.getPk()))
              .length(f.getLength() != null ? f.getLength() : 0)
              .build());
    }

    return DatabaseTable.builder()
        .tableName(entity.getCode())
        .tableComment(entity.getDisplayName() + " (物理表: " + entity.getTableName() + ")")
        .columns(columns)
        .deliveryMode(entity.getDeliveryMode() != null ? entity.getDeliveryMode() : 0)
        .build();
  }
}
