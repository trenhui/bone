package com.bone.tool.codegen.infrastructure.persistence;

import com.bone.core.model.PageResult;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 代码生成表配置仓库默认实现
 * 使用JdbcTemplate提供基础CRUD功能
 *
 * @author bone-team
 */
@Repository
public class DefaultCodegenTableRepository implements CodegenTableRepository {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void deleteById(Long id) {
        String sql = "UPDATE codegen_table SET deleted = true, update_time = ? WHERE id = ?";
        jdbcTemplate.update(sql, new java.sql.Timestamp(System.currentTimeMillis()), id);
    }

    @Override
    public Optional<CodegenTable> findById(Long id) {
        String sql = "SELECT * FROM codegen_table WHERE id = ? AND deleted = false";
        List<CodegenTable> results = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(CodegenTable.class), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Long save(CodegenTable table) {
        String sql = "INSERT INTO codegen_table (datasource_id, table_name, table_comment, " +
                "module_name, package_name, business_name, class_name, class_comment, author, " +
                "template_type, scene, parent_menu_id, master_table_id, sub_join_column_id, " +
                "sub_join_many, tree_parent_column_id, tree_name_column_id, code_files, " +
                "create_time, update_time, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, table.getDatasourceId());
            ps.setString(2, table.getTableName());
            ps.setString(3, table.getTableComment());
            ps.setString(4, table.getModuleName());
            ps.setString(5, table.getPackageName());
            ps.setString(6, table.getBusinessName());
            ps.setString(7, table.getClassName());
            ps.setString(8, table.getClassComment());
            ps.setString(9, table.getAuthor());
            ps.setObject(10, table.getTemplateType());
            ps.setObject(11, table.getScene());
            ps.setObject(12, table.getParentMenuId());
            ps.setObject(13, table.getMasterTableId());
            ps.setObject(14, table.getSubJoinColumnId());
            ps.setObject(15, table.getSubJoinMany());
            ps.setObject(16, table.getTreeParentColumnId());
            ps.setObject(17, table.getTreeNameColumnId());
            // codeFiles 暂时存储为null，后续可扩展为JSON存储
            ps.setObject(18, null);
            ps.setTimestamp(19, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(20, new java.sql.Timestamp(System.currentTimeMillis()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public void update(CodegenTable table) {
        String sql = "UPDATE codegen_table SET datasource_id = ?, table_name = ?, table_comment = ?, " +
                "module_name = ?, package_name = ?, business_name = ?, class_name = ?, class_comment = ?, " +
                "author = ?, template_type = ?, scene = ?, parent_menu_id = ?, master_table_id = ?, " +
                "sub_join_column_id = ?, sub_join_many = ?, tree_parent_column_id = ?, tree_name_column_id = ?, " +
                "code_files = ?, update_time = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                table.getDatasourceId(),
                table.getTableName(),
                table.getTableComment(),
                table.getModuleName(),
                table.getPackageName(),
                table.getBusinessName(),
                table.getClassName(),
                table.getClassComment(),
                table.getAuthor(),
                table.getTemplateType(),
                table.getScene(),
                table.getParentMenuId(),
                table.getMasterTableId(),
                table.getSubJoinColumnId(),
                table.getSubJoinMany(),
                table.getTreeParentColumnId(),
                table.getTreeNameColumnId(),
                table.getCodeFiles(),
                new java.sql.Timestamp(System.currentTimeMillis()),
                table.getId());
    }

    @Override
    public CodegenTable findOneByCriteria(Object criteria) {
        // 简单实现，返回null，实际使用中可扩展
        return null;
    }

    @Override
    public PageResult<CodegenTable> pageByCriteria(Object criteria) {
        // 简单实现，返回空结果
        return PageResult.of(List.of(), 0L, 1, 10);
    }

    @Override
    public List<CodegenTable> findByCriteria(Object criteria) {
        // 简单实现，返回空列表
        return List.of();
    }

    @Override
    public List<CodegenTable> selectListByDataSourceConfigId(Long dataSourceConfigId) {
        String sql = "SELECT * FROM codegen_table WHERE datasource_id = ? AND deleted = false ORDER BY create_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(CodegenTable.class), dataSourceConfigId);
    }
}
