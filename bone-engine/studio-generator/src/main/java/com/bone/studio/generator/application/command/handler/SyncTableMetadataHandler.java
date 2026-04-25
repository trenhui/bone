package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCmd;
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

@Component
@RequiredArgsConstructor
@Capability(name = "syncTableMetadata", description = "同步表元数据", inputSchema = "{}", outputSchema = "{}")
public class SyncTableMetadataHandler {

    private final DatabaseMetadataGateway metadataGateway;
    private final DataSourceRepository dataSourceRepository;
    private final GenTableMetadataRepository tableMetadataRepo;

    @Transactional
    public void handle(SyncTableMetadataCmd cmd) {
        // 1. 加载数据源实体
        DataSource dataSource = dataSourceRepository.findById(cmd.getDataSourceId());
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource not found: " + cmd.getDataSourceId());
        }
        
        // 2. 通过 Gateway 读取物理表结构
        List<DatabaseTable> dbTables = metadataGateway.loadTables(dataSource);
        
        // 3. 同步表元数据
        for (DatabaseTable dbTable : dbTables) {
            // 查找现有元数据
            GenTableMetadata metadata = findExistingMetadata(dataSource.getId(), dbTable.getTableName());
            if (metadata == null) {
                metadata = GenTableMetadata.create(
                        System.currentTimeMillis(),
                        0L,
                        dataSource.getId(),
                        dbTable
                );
            } else {
                // 更新现有元数据
                metadata.syncColumns(dbTable.getColumns());
            }
            tableMetadataRepo.save(metadata);
        }
    }

    private GenTableMetadata findExistingMetadata(String dataSourceId, String tableName) {
        return tableMetadataRepo.findByDataSourceIdAndTableName(dataSourceId, tableName);
    }
}
