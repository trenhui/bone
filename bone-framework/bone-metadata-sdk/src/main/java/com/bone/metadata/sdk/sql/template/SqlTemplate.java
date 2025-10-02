package com.bone.metadata.sdk.sql.template;

import com.bone.metadata.sdk.domain.annotation.SqlType;
import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import lombok.*;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 表示 SQL 模板的实体类，包含模板内容、参数、元数据和处理/执行所需的属性。
 * 该类设计为部分不可变（通过 Builder 构建），支持序列化以用于分布式缓存，
 * 并提供扩展性以支持多种模板来源和格式（如 YAML、SQL、MYBATIS）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlTemplate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // 检测动态 SQL 标签的正则表达式
    public static final Pattern DYNAMIC_TAG_PATTERN = Pattern.compile(
            "<(where|if|foreach)(?:\\s+[^>]*)?>", Pattern.CASE_INSENSITIVE);

    /**
     * 模板的唯一标识符，例如 com.example.BarRepo.findById 或 user/searchUsersPaged。
     */
    private String id;

    /**
     * 原始 SQL 或模板内容。
     */
    private String sql;

    /**
     * 模板来源 URI，例如 annotation://, classpath://, http://。
     */
    private String source;

    /**
     * 模板的格式，例如 SQL, MYBATIS, YAML_SQL。
     */
    @Builder.Default
    private SqlTemplateType sqlTemplateType = SqlTemplateType.SQL;

    /**
     * SQL 操作类型，例如 SELECT, INSERT, UPDATE, DELETE, CTE, UNKNOWN。
     */
    @Builder.Default
    private SqlType sqlType = SqlType.UNKNOWN;//"UNKNOWN";

    /**
     * 模板预定义的参数（例如 YAML 中的 parameters 字段）。
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * 用于可观测性、调试和操作控制的元数据（例如 origin, fetchedAt, complexity）。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 验证模板的核心字段是否有效。
     *
     * @throws IllegalStateException 如果模板无效。
     */
    public void validate() {
        if (!StringUtils.hasText(id)) {
            throw new IllegalStateException("模板 ID 不能为空");
        }
        if (!StringUtils.hasText(sql)) {
            throw new IllegalStateException("模板 " + id + " 的 SQL 内容不能为空");
        }
        if (!StringUtils.hasText(source)) {
            throw new IllegalStateException("模板 " + id + " 的来源 URI 不能为空");
        }
        if (sqlTemplateType == null) {
            throw new IllegalStateException("模板 " + id + " 的 SQL 模板类型不能为空");
        }
        if (parameters != null) {
            parameters.forEach((key, value) -> {
                if (key == null || value == null) {
                    throw new IllegalStateException("模板 " + id + " 的参数键或值不能为空");
                }
                if (value instanceof String str && (str.contains(";") || str.matches("(?i).*\\b(SELECT|DROP)\\b.*"))) {
                    throw new IllegalStateException("模板 " + id + " 的参数值不安全: " + key);
                }
            });
        }
    }

    /**
     * 返回参数的不可变视图。
     *
     * @return 不可变的参数映射。
     */
    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }


    /**
     * 合并运行时参数到模板预定义参数。
     *
     * @param runtimeParams 运行时参数。
     */
    public void mergeParameters(Map<String, Object> runtimeParams) {
        if (runtimeParams != null) {
            Map<String, Object> merged = new HashMap<>(parameters);
            runtimeParams.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
            this.parameters = merged;
        }
    }

    /**
     * 添加或更新参数的键值对。
     *
     * @param key   参数键。
     * @param value 参数值。
     */
    public void addParameter(String key, Object value) {
        Objects.requireNonNull(key, "参数键不能为空");
        if (value != null) {
            parameters.put(key, value);
        }
    }

    /**
     * 批量添加参数。
     *
     * @param newParameters 要添加的参数映射。
     */
    public void addAllParameters(Map<String, Object> newParameters) {
        if (newParameters != null) {
            newParameters.forEach((key, value) -> {
                if (value != null) {
                    parameters.put(key, value);
                }
            });
        }
    }

    /**
     * 返回元数据的不可变视图。
     *
     * @return 不可变的元数据映射。
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }


    /**
     * 添加或更新元数据的键值对。
     *
     * @param key   元数据键。
     * @param value 元数据值。
     */
    public void addMetadata(String key, Object value) {
        Objects.requireNonNull(key, "元数据键不能为空");
        metadata.put(key, value);
    }


    /**
     * 检查模板内容是否与提供的校验和匹配。
     *
     * @param checksum MD5 校验和。
     * @return 如果校验和匹配返回 true，否则返回 false。
     */
    public boolean matchesChecksum(String checksum) {
        if (checksum == null || sql == null) {
            return false;
        }
        String currentChecksum = DigestUtils.md5DigestAsHex(sql.getBytes());
        return checksum.equals(currentChecksum);
    }

    /**
     * 创建模板的副本，并更新版本元数据。
     *
     * @param newVersion 新的版本字符串。
     * @return 更新后的 SqlTemplate 实例。
     */
    public SqlTemplate withVersion(String newVersion) {
        SqlTemplate sqlTemplate = SqlTemplate.builder()
                .id(this.id)
                .sql(this.sql)
                .source(this.source)
                .sqlTemplateType(this.sqlTemplateType)
                .sqlType(this.sqlType)
                .parameters(new HashMap<>(this.parameters))
                .metadata(new HashMap<>(this.metadata))
                .build();
        sqlTemplate.addMetadata("version", newVersion);
        return sqlTemplate;
    }

    /**
     * 检查模板是否包含动态 SQL 标签（基于内容）。
     *
     * @return 如果包含动态标签返回 true，否则返回 false。
     */
    public boolean isDynamic() {
        return DYNAMIC_TAG_PATTERN.matcher(sql).find();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqlTemplate)) return false;
        SqlTemplate that = (SqlTemplate) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(sql, that.sql) &&
                Objects.equals(source, that.source) &&
                sqlTemplateType == that.sqlTemplateType &&
                Objects.equals(sqlType, that.sqlType) &&
                Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sql, source, sqlTemplateType, sqlType, parameters);
    }

    @Override
    public String toString() {
        return "SqlTemplate{" +
                "templateId='" + id + '\'' +
                ", source='" + source + '\'' +
                ", sqlTemplateType=" + sqlTemplateType +
                ", sqlType='" + sqlType + '\'' +
                ", parameters=" + parameters +
                ", metadata=" + metadata +
                '}';
    }
}