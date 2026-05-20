package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCmd;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.CollectionUtils;

@Component
@RequiredArgsConstructor
@Capability(name = "syncTableMetadata", description = "同步表元数据", inputSchema = "{}", outputSchema = "{}")
public class SyncTableMetadataHandler {

    private final DatabaseMetadataGateway metadataGateway;
    private final DataSourceRepository dataSourceRepository;
    private final GenTableMetadataRepository tableMetadataRepo;

    @Transactional
    public void handle(SyncTableMetadataCmd cmd) {
        Long dataSourcePk = StudioIds.parseRequired(cmd.getDataSourceId());
        DataSource dataSource = dataSourceRepository.findById(dataSourcePk);
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + cmd.getDataSourceId());
        }

        List<DatabaseTable> dbTables = metadataGateway.loadTables(dataSource);
        if (!CollectionUtils.isEmpty(cmd.getTableNames())) {
            Set<String> wanted =
                    cmd.getTableNames().stream()
                            .filter(n -> n != null && !n.isBlank())
                            .collect(Collectors.toSet());
            dbTables =
                    dbTables.stream()
                            .filter(t -> wanted.contains(t.getTableName()))
                            .toList();
        }
        String dataSourceKey = StudioIds.dataSourceKey(dataSourcePk);

        for (DatabaseTable dbTable : dbTables) {
            GenTableMetadata metadata = findExistingMetadata(dataSourceKey, dbTable.getTableName());
            if (metadata == null) {
                metadata = GenTableMetadata.create(
                        DistributedIdGenerator.generateLongId(),
                        0L,
                        dataSourceKey,
                        dbTable);
                tableMetadataRepo.insert(metadata);
            } else {
                metadata.syncColumns(dbTable.getColumns());
                tableMetadataRepo.update(metadata);
            }
        }
    }

    private GenTableMetadata findExistingMetadata(String dataSourceKey, String tableName) {
        try {
            return tableMetadataRepo.findOneByCriteria(Criteria.<GenTableMetadata>create()
                    .eq("dataSourceId", dataSourceKey)
                    .eq("originalTableName", tableName));
        } catch (MultipleResultsException e) {
            throw new IllegalStateException("duplicate table metadata: " + dataSourceKey + "/" + tableName, e);
        }
    }
}
