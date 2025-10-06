package com.bone.metadata.sdk.test.domain;

import com.bone.core.util.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataPermission extends Permission {
    // 扩展属性键定义（原始部分）
    public static final String DATA_SCOPE = "data_scope";
    public static final String VISIBLE_FIELDS = "visible_fields";
    public static final String ALLOWED_OPS = "allowed_ops";
    public static final String ROW_FILTER = "row_filter";

    // 新增扩展属性键定义
    public static final String ACCESS_SCHEDULE = "access_schedule";
    public static final String REQUIRED_ROLES = "required_roles";
    public static final String DATA_SENSITIVITY_LEVEL = "data_sensitivity_level";
    public static final String GEO_RESTRICTIONS = "geo_restrictions";
    public static final String TEMPORARY_TOKEN = "temporary_token";

    // 初始化扩展属性存储
    public DataPermission() {
        mergeExtraProperties(new ConcurrentHashMap<>());
    }

    // 数据作用域相关方法

    /**
     * 设置数据作用域（线程安全）
     *
     * @param scope 可选值: ALL/DEPARTMENT/CUSTOM
     */
    public void setDataScope(DataScope scope) {
        putExtraProperty(DATA_SCOPE, scope.toString());
    }

    /**
     * 获取当前数据作用域
     *
     * @return 数据作用域，默认值为 ALL
     */
    public DataScope getDataScope() {
        return getExtraProperty(DATA_SCOPE, DataScope.class).orElse(DataScope.ALL);
    }

    // 可见字段相关方法

    /**
     * 设置可见字段
     *
     * @param fields 可见字段列表
     */
    public void setVisibleFields(List<String> fields) {
        putExtraProperty(VISIBLE_FIELDS, fields != null ? JsonUtil.toJson(fields) : null);
    }

    /**
     * 获取可见字段列表
     *
     * @return 可见字段列表，默认值为空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getVisibleFields() {
        return getExtraProperty(VISIBLE_FIELDS, List.class).orElseGet(ArrayList::new);
    }

    /**
     * 添加可见字段
     * @param field 可见字段（如department_id）
     */
    public void addVisibleField(String field) {
        List<String> visibleFields = getVisibleFields();
        visibleFields.add(field);
        putExtraProperty(VISIBLE_FIELDS, visibleFields);
    }

    // 允许操作相关方法

    /**
     * 设置允许的操作
     *
     * @param ops 允许的操作列表
     */
    public void setAllowedOps(List<String> ops) {
        putExtraProperty(ALLOWED_OPS, ops != null ? List.copyOf(ops) : null);
    }

    /**
     * 获取允许的操作列表
     *
     * @return 允许的操作列表，默认值为空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getAllowedOps() {
        return getExtraProperty(ALLOWED_OPS, List.class).orElseGet(ArrayList::new);
    }

    // 行级过滤相关方法

    /**
     * 设置行级过滤条件
     *
     * @param filter 行级过滤条件
     */
    public void setRowFilter(Map<String, String> filter) {
        putExtraProperty(ROW_FILTER, filter != null ? Map.copyOf(filter) : null);
    }

    /**
     * 获取行级过滤条件
     *
     * @return 行级过滤条件，默认值为空映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getRowFilter() {
        return getExtraProperty(ROW_FILTER, Map.class).orElseGet(ConcurrentHashMap::new);
    }

    /**
     * 添加行级过滤条件
     * @param field 过滤字段（如department_id）
     * @param value 匹配值或表达式
     */
    public void addRowFilter(String field, String value) {
        Map<String, String> filters = getRowFilter();
        filters.put(field, value);
        putExtraProperty(ROW_FILTER, filters);
    }

    // 访问时间计划相关方法

    /**
     * 设置数据访问的时间计划
     *
     * @param schedule 时间计划（如 cron 表达式或时间段列表）
     */
    public void setAccessSchedule(String schedule) {
        putExtraProperty(ACCESS_SCHEDULE, schedule);
    }

    /**
     * 获取数据访问的时间计划
     *
     * @return 时间计划字符串，默认值为 null（无限制）
     */
    public String getAccessSchedule() {
        return getExtraProperty(ACCESS_SCHEDULE, String.class).orElse(null);
    }

    // 所需角色相关方法

    /**
     * 设置访问数据所需的角色列表
     *
     * @param roles 角色集合
     */
    public void setRequiredRoles(Set<String> roles) {
        putExtraProperty(REQUIRED_ROLES, roles != null ? Set.copyOf(roles) : null);
    }

    /**
     * 获取访问数据所需的角色列表
     *
     * @return 角色集合，默认值为空集合
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRequiredRoles() {
        return getExtraProperty(REQUIRED_ROLES, Set.class).orElseGet(HashSet::new);
    }

    // 数据敏感性级别相关方法

    /**
     * 设置数据的敏感性级别
     *
     * @param level 敏感性级别（1-5，1最低，5最高）
     */
    public void setDataSensitivityLevel(Integer level) {
        if (level != null && (level < 1 || level > 5)) {
            throw new IllegalArgumentException("Sensitivity level must be between 1 and 5");
        }
        putExtraProperty(DATA_SENSITIVITY_LEVEL, level);
    }

    /**
     * 获取数据的敏感性级别
     *
     * @return 敏感性级别，默认值为 null（未设置）
     */
    public Integer getDataSensitivityLevel() {
        return getExtraProperty(DATA_SENSITIVITY_LEVEL, Integer.class).orElse(null);
    }

    // 地理位置限制相关方法

    /**
     * 设置允许访问的地理位置区域
     *
     * @param geoRestrictions 地理位置限制列表（如国家代码 ISO 3166-1 alpha-2）
     */
    public void setGeoRestrictions(List<String> geoRestrictions) {
        putExtraProperty(GEO_RESTRICTIONS, geoRestrictions != null ? List.copyOf(geoRestrictions) : null);
    }

    /**
     * 获取允许访问的地理位置区域
     *
     * @return 地理位置限制列表，默认值为空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getGeoRestrictions() {
        return getExtraProperty(GEO_RESTRICTIONS, List.class).orElseGet(ArrayList::new);
    }

    // 临时访问令牌相关方法

    /**
     * 设置临时访问令牌及其有效期
     *
     * @param token 临时令牌，格式为 "token:expiration"（expiration 为 Unix 时间戳）
     */
    public void setTemporaryToken(String token) {
        putExtraProperty(TEMPORARY_TOKEN, token);
    }

    /**
     * 获取临时访问令牌
     *
     * @return 临时令牌字符串，默认值为 null
     */
    public String getTemporaryToken() {
        return getExtraProperty(TEMPORARY_TOKEN, String.class).orElse(null);
    }

    // region 枚举定义
    public enum DataScope {
        ALL,       // 全部数据
        DEPARTMENT,// 本部门数据
        CUSTOM     // 自定义过滤
    }
}