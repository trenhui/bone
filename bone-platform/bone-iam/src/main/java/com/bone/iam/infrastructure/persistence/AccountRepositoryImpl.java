package com.bone.iam.infrastructure.persistence;

import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Optional<Account> findByUsername(String username) {
        String sql = """
            SELECT * FROM iam_account WHERE username = :username
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("username", username);
        return jdbcTemplate.query(sql, params, rs -> {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        });
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        try {
            Account account = Account.create(
                rs.getLong("id"),
                new Username(rs.getString("username")),
                rs.getString("password_hash"),
                new Email(rs.getString("email")),
                rs.getString("phone"),
                rs.getString("real_name"),
                rs.getLong("tenant_id")
            );

            setField(account, "id", rs.getLong("id"));
            setField(account, "avatarUrl", rs.getString("avatar_url"));

            AccountStatus status = resolveAccountStatus(rs.getObject("status"));
            setField(account, "status", status);
            setField(account, "isAdmin", rs.getBoolean("is_admin"));
            setField(account, "loginFailCount", rs.getInt("login_fail_count"));

            Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
            setField(account, "lastLoginAt", lastLoginAt != null ? lastLoginAt.toLocalDateTime() : null);
            setField(account, "lastLoginIp", rs.getString("last_login_ip"));

            Timestamp lockedUntil = rs.getTimestamp("locked_until");
            setField(account, "lockedUntil", lockedUntil != null ? lockedUntil.toLocalDateTime() : null);

            Timestamp pwdUpdatedAt = rs.getTimestamp("pwd_updated_at");
            setField(account, "pwdUpdatedAt", pwdUpdatedAt != null ? pwdUpdatedAt.toLocalDateTime() : null);

            Timestamp createTime = rs.getTimestamp("create_time");
            setField(account, "createTime", createTime != null ? createTime.toLocalDateTime() : null);

            Timestamp updateTime = rs.getTimestamp("update_time");
            setField(account, "updateTime", updateTime != null ? updateTime.toLocalDateTime() : null);

            return account;
        } catch (Exception e) {
            throw new SQLException("Failed to map Account", e);
        }
    }

    private static AccountStatus resolveAccountStatus(Object statusValue) {
        if (statusValue == null) {
            return AccountStatus.ENABLED;
        }
        if (statusValue instanceof Number number) {
            return AccountStatus.of(number.intValue());
        }
        String statusStr = statusValue.toString().trim();
        if (statusStr.isEmpty()) {
            return AccountStatus.ENABLED;
        }
        try {
            return AccountStatus.valueOf(statusStr);
        } catch (IllegalArgumentException ignored) {
            return AccountStatus.of(Integer.parseInt(statusStr));
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Override
    public <S extends Account> S update(S entity) {
        return entity;
    }

    @Override
    public <S extends Account> S insert(S entity) {
        return entity;
    }

    @Override
    public <S extends Account> S save(S entity) {
        return entity;
    }

    @Override
    public void deleteById(Long id) {
    }

    @Override
    public Optional<Account> findById(Long id) {
        String sql = """
            SELECT * FROM iam_account WHERE id = :id
            """;
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        return jdbcTemplate.query(sql, params, rs -> {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        });
    }
}
