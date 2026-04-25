package com.bone.studio.generator.domain.gateway;

import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.TableColumn;

import java.util.List;

public interface DatabaseMetadataGateway {
    boolean testConnection(DataSource dataSource);
    List<DatabaseTable> loadTables(DataSource dataSource);
    List<TableColumn> loadTableColumns(DataSource dataSource, String tableName);
}
