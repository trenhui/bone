package com.bone.tool.codegen.infrastructure.persistence;

import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
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
 * 数据源配置仓库默认实现
 * 使用JdbcTemplate提供基础CRUD功能
 *
 * @author bone-team
 */
@Repository
public class DefaultDataSourceConfigRepository implements DataSourceConfigRepository {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Datasource> findById(Long id) {
        String sql = "SELECT * FROM codegen_datasource WHERE id = ? AND deleted = false";
        List<Datasource> results = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(Datasource.class), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Long save(Datasource datasource) {
        String sql = "INSERT INTO codegen_datasource (name, url, username, password, driver_class_name, " +
                "create_time, update_time, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, false)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, datasource.getName());
            ps.setString(2, datasource.getUrl());
            ps.setString(3, datasource.getUsername());
            ps.setString(4, datasource.getPassword());
            ps.setString(5, datasource.getDriverClassName());
            ps.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    @Override
    public void update(Datasource datasource) {
        String sql = "UPDATE codegen_datasource SET name = ?, url = ?, username = ?, password = ?, " +
                "driver_class_name = ?, update_time = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                datasource.getName(),
                datasource.getUrl(),
                datasource.getUsername(),
                datasource.getPassword(),
                datasource.getDriverClassName(),
                new java.sql.Timestamp(System.currentTimeMillis()),
                datasource.getId());
    }

    @Override
    public void deleteById(Long id) {
        String sql = "UPDATE codegen_datasource SET deleted = true, update_time = ? WHERE id = ?";
        jdbcTemplate.update(sql, new java.sql.Timestamp(System.currentTimeMillis()), id);
    }

    @Override
    public List<Datasource> findAll() {
        String sql = "SELECT * FROM codegen_datasource WHERE deleted = false ORDER BY create_time DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Datasource.class));
    }

    @Override
    public List<Datasource> findByCondition(Object condition) {
        // 简单实现，返回所有结果，复杂条件由上层处理
        return findAll();
    }
}
