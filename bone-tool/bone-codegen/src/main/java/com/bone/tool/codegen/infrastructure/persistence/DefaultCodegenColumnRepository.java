package com.bone.tool.codegen.infrastructure.persistence;

import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
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
 * 代码生成列配置仓库默认实现
 * 使用JdbcTemplate提供基础CRUD功能
 *
 * @author bone-team
 */
@Repository
public class DefaultCodegenColumnRepository implements CodegenColumnRepository {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void deleteById(Long id) {
        String sql = "UPDATE codegen_column SET deleted = true, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, new java.sql.Timestamp(System.currentTimeMillis()), id);
    }

    @Override
    public Optional<CodegenColumn> findById(Long id) {
        String sql = "SELECT * FROM codegen_column WHERE id = ? AND deleted = false";
        List<CodegenColumn> results = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(CodegenColumn.class), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public CodegenColumn save(CodegenColumn column) {
        String sql = "INSERT INTO codegen_column (table_id, column_name, column_comment, data_type, " +
                "java_type, java_field, html_type, primary_key, nullable, auto_increment, " +
                "created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, column.getTableId());
            ps.setString(2, column.getColumnName());
            ps.setString(3, column.getColumnComment());
            ps.setString(4, column.getDataType());
            ps.setString(5, column.getJavaType());
            ps.setString(6, column.getJavaField());
            ps.setString(7, column.getHtmlType());
            ps.setBoolean(8, column.getPrimaryKey() != null ? column.getPrimaryKey() : false);
            ps.setBoolean(9, column.getNullable() != null ? column.getNullable() : true);
            ps.setBoolean(10, column.getAutoIncrement() != null ? column.getAutoIncrement() : false);
            ps.setTimestamp(11, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(12, new java.sql.Timestamp(System.currentTimeMillis()));
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            column.setId(keyHolder.getKey().longValue());
        }
        return column;
    }

    @Override
    public void update(CodegenColumn column) {
        String sql = "UPDATE codegen_column SET table_id = ?, column_name = ?, column_comment = ?, " +
                "data_type = ?, java_type = ?, java_field = ?, html_type = ?, primary_key = ?, " +
                "nullable = ?, auto_increment = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                column.getTableId(),
                column.getColumnName(),
                column.getColumnComment(),
                column.getDataType(),
                column.getJavaType(),
                column.getJavaField(),
                column.getHtmlType(),
                column.getPrimaryKey(),
                column.getNullable(),
                column.getAutoIncrement(),
                new java.sql.Timestamp(System.currentTimeMillis()),
                column.getId());
    }

    @Override
    public List<CodegenColumn> findAll() {
        String sql = "SELECT * FROM codegen_column WHERE deleted = false ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(CodegenColumn.class));
    }

    @Override
    public List<CodegenColumn> findByCriteria(Object criteria) {
        return findAll();
    }

    /**
     * 根据表ID查询所有列配置
     * @param tableId 表ID
     * @return 列配置列表
     */
    public List<CodegenColumn> findByCriteria(Long tableId) {
        String sql = "SELECT * FROM codegen_column WHERE table_id = ? AND deleted = false ORDER BY id ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(CodegenColumn.class), tableId);
    }
}
