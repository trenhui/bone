package com.bone.tools.codegen.infrastructure.config;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tools.codegen.domain.enums.CodegenColumnHtmlTypeEnum;
import com.bone.tools.codegen.domain.enums.CodegenColumnListConditionEnum;
import com.bone.tools.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.bone.tools.codegen.domain.entity.BaseDO;
import com.bone.tools.codegen.domain.entity.CodegenColumnDO;
import com.bone.tools.codegen.domain.entity.CodegenTableDO;
import com.bone.tools.codegen.application.CodegenConvert;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static cn.hutool.core.text.CharSequenceUtil.*;
import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.hutool.core.util.RandomUtil.randomInt;

/**
 * 代码生成器的 Builder，负责：
 * 1. 将数据库的表 {@link TableInfo} 定义，构建成 {@link CodegenTableDO}
 * 2. 将数据库的列 {@link TableField} 构定义，建成 {@link CodegenColumnDO}
 */
@Component
public class CodegenBuilder {

    /**
     * 字段名与 {@link CodegenColumnListConditionEnum} 的默认映射
     * 注意，字段的匹配以后缀的方式
     */
    private static final Map<String, CodegenColumnListConditionEnum> COLUMN_LIST_OPERATION_CONDITION_MAPPINGS =
            MapUtil.<String, CodegenColumnListConditionEnum>builder()
                    .put("name", CodegenColumnListConditionEnum.LIKE)
                    .put("time", CodegenColumnListConditionEnum.BETWEEN)
                    .put("date", CodegenColumnListConditionEnum.BETWEEN)
                    .build();

    /**
     * 字段名与 {@link CodegenColumnHtmlTypeEnum} 的默认映射
     * 注意，字段的匹配以后缀的方式
     */
    private static final Map<String, CodegenColumnHtmlTypeEnum> COLUMN_HTML_TYPE_MAPPINGS =
            MapUtil.<String, CodegenColumnHtmlTypeEnum>builder()
                    .put("status", CodegenColumnHtmlTypeEnum.RADIO)
                    .put("sex", CodegenColumnHtmlTypeEnum.RADIO)
                    .put("type", CodegenColumnHtmlTypeEnum.SELECT)
                    .put("image", CodegenColumnHtmlTypeEnum.IMAGE_UPLOAD)
                    .put("file", CodegenColumnHtmlTypeEnum.FILE_UPLOAD)
                    .put("content", CodegenColumnHtmlTypeEnum.EDITOR)
                    .put("description", CodegenColumnHtmlTypeEnum.EDITOR)
                    .put("demo", CodegenColumnHtmlTypeEnum.EDITOR)
                    .put("time", CodegenColumnHtmlTypeEnum.DATETIME)
                    .put("date", CodegenColumnHtmlTypeEnum.DATETIME)
                    .build();

    /**
     * 多租户编号的字段名
     */
    public static final String TENANT_ID_FIELD = "tenantId";
    /**
     * {@link BaseDO} 的字段
     */
    public static final Set<String> BASE_DO_FIELDS = new HashSet<>();
    /**
     * 新增操作，不需要传递的字段
     */
    private static final Set<String> CREATE_OPERATION_EXCLUDE_COLUMN = Stream.of("id").collect(Collectors.toSet());

    /**
     * 修改操作，不需要传递的字段
     */
    private static final Set<String> UPDATE_OPERATION_EXCLUDE_COLUMN = new HashSet<>();
    /**
     * 列表操作的条件，不需要传递的字段
     */
    private static final Set<String> LIST_OPERATION_EXCLUDE_COLUMN = Stream.of("id").collect(Collectors.toSet());
    /**
     * 列表操作的结果，不需要返回的字段
     */
    private static final Set<String> LIST_OPERATION_RESULT_EXCLUDE_COLUMN = new HashSet<>();

    static {
        Arrays.stream(ReflectUtil.getFields(ExtensibleObject.class)).forEach(field -> BASE_DO_FIELDS.add(field.getName()));
        BASE_DO_FIELDS.add(TENANT_ID_FIELD);
        // 处理 OPERATION 相关的字段
        CREATE_OPERATION_EXCLUDE_COLUMN.addAll(BASE_DO_FIELDS);
        UPDATE_OPERATION_EXCLUDE_COLUMN.addAll(BASE_DO_FIELDS);
        LIST_OPERATION_EXCLUDE_COLUMN.addAll(BASE_DO_FIELDS);
        LIST_OPERATION_EXCLUDE_COLUMN.remove("createTime"); // 创建时间，还是可能需要传递的
        LIST_OPERATION_RESULT_EXCLUDE_COLUMN.addAll(BASE_DO_FIELDS);
        LIST_OPERATION_RESULT_EXCLUDE_COLUMN.remove("createTime"); // 创建时间，还是需要返回的
    }

    public CodegenTableDO buildTable(TableInfo tableInfo) {
        CodegenTableDO table = CodegenConvert.INSTANCE.convert(tableInfo);
        initTableDefault(table);
        return table;
    }

    /**
     * 初始化 Table 表的默认字段
     *
     * @param table 表定义
     */
    private void initTableDefault(CodegenTableDO table) {
        // 以 system_dept 举例子。moduleName 为 system、businessName 为 dept、className 为 Dept
        // 如果希望以 System 前缀，则可以手动在【代码生成 - 修改生成配置 - 基本信息】，将实体类名称改为 SystemDept 即可
        String tableName = table.getTableName().toLowerCase();
        // 第一步，_ 前缀的前面，作为 module 名字；第二步，moduleName 必须小写；
        table.setModuleName(subBefore(tableName, '_', false).toLowerCase());
        // 第一步，第一个 _ 前缀的后面，作为 module 名字; 第二步，可能存在多个 _ 的情况，转换成驼峰; 第三步，businessName 必须小写；
        table.setBusinessName(toCamelCase(subAfter(tableName, '_', false)).toLowerCase());
        // 驼峰 + 首字母大写；第一步，第一个 _ 前缀的后面，作为 class 名字；第二步，驼峰命名
        table.setClassName(upperFirst(toCamelCase(subAfter(tableName, '_', false))));
        // 去除结尾的表，作为类描述
        try {
            table.setClassComment(StrUtil.removeSuffixIgnoreCase(table.getTableComment(), "表"));
            
            // 使用枚举的type字段值
            table.setTemplateType(CodegenTemplateTypeEnum.ONE.getType());
        } catch (Exception e) {
            // 忽略异常
        }
    }
    
    public List<CodegenColumnDO> buildColumns(Long tableId, List<TableField> tableFields) {
        List<CodegenColumnDO> columns = CodegenConvert.INSTANCE.convertList(tableFields);
        int index = 1;
        for (CodegenColumnDO column : columns) {
            try {
                // 使用反射设置字段值
                ReflectUtil.setFieldValue(column, "tableId", tableId);
                ReflectUtil.setFieldValue(column, "ordinalPosition", index++);
                // 特殊处理：Byte => Integer
                String javaType = (String) ReflectUtil.getFieldValue(column, "javaType");
                if (Byte.class.getSimpleName().equals(javaType)) {
                    ReflectUtil.setFieldValue(column, "javaType", Integer.class.getSimpleName());
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
            // 初始化 Column 列的默认字段
            processColumnOperation(column); // 处理 CRUD 相关的字段的默认值
            processColumnUI(column); // 处理 UI 相关的字段的默认值
            processColumnExample(column); // 处理字段的 swagger example 示例
        }
        return columns;
    }

    private void processColumnOperation(CodegenColumnDO column) {
        try {
            // 获取需要的字段值
            String javaField = (String) ReflectUtil.getFieldValue(column, "javaField");
            Boolean primaryKey = (Boolean) ReflectUtil.getFieldValue(column, "primaryKey");
            
            // 处理 createOperation 字段
            ReflectUtil.setFieldValue(column, "createOperation", !CREATE_OPERATION_EXCLUDE_COLUMN.contains(javaField)
                    && !primaryKey); // 对于主键，创建时无需传递
            
            // 处理 updateOperation 字段
            ReflectUtil.setFieldValue(column, "updateOperation", !UPDATE_OPERATION_EXCLUDE_COLUMN.contains(javaField)
                    || primaryKey); // 对于主键，更新时需要传递
            
            // 处理 listOperation 字段
            ReflectUtil.setFieldValue(column, "listOperation", !LIST_OPERATION_EXCLUDE_COLUMN.contains(javaField)
                    && !primaryKey); // 对于主键，列表过滤不需要传递
            
            // 处理 listOperationCondition 字段
            Object listOperationCondition = null;
            for (Map.Entry<String, CodegenColumnListConditionEnum> entry : COLUMN_LIST_OPERATION_CONDITION_MAPPINGS.entrySet()) {
                if (StrUtil.endWithIgnoreCase(javaField, entry.getKey())) {
                    try {
                        // 使用反射获取condition字段值
                        listOperationCondition = ReflectUtil.getFieldValue(entry.getValue(), "condition");
                    } catch (Exception e) {
                        // 如果反射失败，使用枚举名作为备选
                        listOperationCondition = entry.getValue().name();
                    }
                    break;
                }
            }
            if (listOperationCondition == null) {
                try {
                    // 使用反射获取EQ枚举的condition字段值
                    listOperationCondition = ReflectUtil.getFieldValue(CodegenColumnListConditionEnum.EQ, "condition");
                } catch (Exception e) {
                    // 如果反射失败，使用"EQ"作为备选
                    listOperationCondition = "EQ";
                }
            }
            ReflectUtil.setFieldValue(column, "listOperationCondition", listOperationCondition);
            
            // 处理 listOperationResult 字段
            ReflectUtil.setFieldValue(column, "listOperationResult", !LIST_OPERATION_RESULT_EXCLUDE_COLUMN.contains(javaField));
        } catch (Exception e) {
            // 忽略反射异常
        }
    }

    private void processColumnUI(CodegenColumnDO column) {
        try {
            // 获取需要的字段值
            String javaField = (String) ReflectUtil.getFieldValue(column, "javaField");
            String javaType = (String) ReflectUtil.getFieldValue(column, "javaType");
            
            // 基于后缀进行匹配
            Object htmlType = null;
            for (Map.Entry<String, CodegenColumnHtmlTypeEnum> entry : COLUMN_HTML_TYPE_MAPPINGS.entrySet()) {
                if (StrUtil.endWithIgnoreCase(javaField, entry.getKey())) {
                    try {
                        // 使用反射获取type字段值
                        htmlType = ReflectUtil.getFieldValue(entry.getValue(), "type");
                    } catch (Exception e) {
                        // 如果反射失败，使用枚举名作为备选
                        htmlType = entry.getValue().name().toLowerCase();
                    }
                    break;
                }
            }
            
            // 如果是 Boolean 类型时，设置为 radio 类型
            if (htmlType == null && Boolean.class.getSimpleName().equals(javaType)) {
                try {
                    htmlType = ReflectUtil.getFieldValue(CodegenColumnHtmlTypeEnum.RADIO, "type");
                } catch (Exception e) {
                    htmlType = "radio";
                }
            }
            
            // 如果是 LocalDateTime 类型，则设置为 datetime 类型
            if (htmlType == null && LocalDateTime.class.getSimpleName().equals(javaType)) {
                try {
                    htmlType = ReflectUtil.getFieldValue(CodegenColumnHtmlTypeEnum.DATETIME, "type");
                } catch (Exception e) {
                    htmlType = "datetime";
                }
            }
            
            // 兜底，设置默认为 input 类型
            if (htmlType == null) {
                try {
                    htmlType = ReflectUtil.getFieldValue(CodegenColumnHtmlTypeEnum.INPUT, "type");
                } catch (Exception e) {
                    htmlType = "input";
                }
            }
            
            ReflectUtil.setFieldValue(column, "htmlType", htmlType);
        } catch (Exception e) {
            // 忽略反射异常
        }
    }

    /**
     * 处理字段的 swagger example 示例
     *
     * @param column 字段
     */
    private void processColumnExample(CodegenColumnDO column) {
        try {
            // 获取需要的字段值
            String javaField = (String) ReflectUtil.getFieldValue(column, "javaField");
            
            // id、price、count 等可能是整数的后缀
            if (StrUtil.endWithAnyIgnoreCase(javaField, "id", "price", "count")) {
                ReflectUtil.setFieldValue(column, "example", String.valueOf(randomInt(1, Short.MAX_VALUE)));
                return;
            }
            // name
            if (StrUtil.endWithIgnoreCase(javaField, "name")) {
                ReflectUtil.setFieldValue(column, "example", randomEle(new String[]{"张三", "李四", "王五", "赵六", "芋艿"}));
                return;
            }
            // status
        } catch (Exception e) {
            // 忽略反射异常
        }
        // 继续处理其他后缀情况
        try {
            String javaField = (String) ReflectUtil.getFieldValue(column, "javaField");
            
            // status、type
            if (StrUtil.endWithAnyIgnoreCase(javaField, "status", "type")) {
                ReflectUtil.setFieldValue(column, "example", randomEle(new String[]{"1", "2"}));
                return;
            }
            // url
            if (StrUtil.endWithIgnoreCase((String)ReflectUtil.getFieldValue(column, "columnName"), "url")) {
                ReflectUtil.setFieldValue(column, "example", "https://www.iocoder.cn");
                return;
            }
            // reason
            if (StrUtil.endWithIgnoreCase((String)ReflectUtil.getFieldValue(column, "columnName"), "reason")) {
                ReflectUtil.setFieldValue(column, "example", randomEle(new String[]{"不喜欢", "不对", "不好", "不香"}));
                return;
            }
            // description、memo、remark
            if (StrUtil.endWithAnyIgnoreCase((String)ReflectUtil.getFieldValue(column, "columnName"), "description", "memo", "remark")) {
                ReflectUtil.setFieldValue(column, "example", randomEle(new String[]{"你猜", "随便", "你说的对"}));
                return;
            }
            // 其他
            ReflectUtil.setFieldValue(column, "example", "示例值");
        } catch (Exception e) {
            // 忽略反射异常
        }
    }
}
