package com.bone.studio.generator.infrastructure.gateway;

import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.domain.gateway.GenTableMetadataReadPort;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.repository.GenColumnMetadataRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link GenTableMetadataReadPort} 实现：读侧 DSL 仅出现在 infrastructure 查询层。 */
@Component
@RequiredArgsConstructor
public class GenTableMetadataReadPortImpl implements GenTableMetadataReadPort {

  private final GenTableMetadataRepository tableMetadataRepository;
  private final GenColumnMetadataRepository columnMetadataRepository;

  @Override
  public Optional<GenTableMetadata> findByDataSourceKeyAndTableName(
      String dataSourceKey, String originalTableName) {
    try {
      GenTableMetadata table =
          tableMetadataRepository.findOneByCriteria(
              Criteria.<GenTableMetadata>create()
                  .eq("dataSourceId", dataSourceKey)
                  .eq("originalTableName", originalTableName));
      if (table == null) {
        return Optional.empty();
      }
      // 列在独立表，必须装配：模板遍历 columns，null 会让 Freemarker 直接抛 InvalidReferenceException
      List<GenColumnMetadata> columns =
          columnMetadataRepository.findByTableMetadataId(table.getId());
      table.attachColumns(columns);
      return Optional.of(table);
    } catch (MultipleResultsException e) {
      throw new IllegalStateException(
          "duplicate table metadata: " + dataSourceKey + "/" + originalTableName, e);
    }
  }
}
