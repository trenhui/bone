package com.bone.tool.codegen.domain.service.generator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.enums.CodegenSceneEnum;
import com.bone.tool.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;
import com.bone.tool.codegen.domain.service.renderer.TemplateRenderer;
import com.bone.tool.codegen.infrastructure.util.ReflectionUtil;

import com.google.common.collect.Maps;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.hutool.core.map.MapUtil.getStr;
import static cn.hutool.core.text.CharSequenceUtil.*;

/**
 * 默认代码生成器实现
 * 提供基于模板的代码生成能力
 */
@Component
public class DefaultCodeGenerator implements CodeGenerator {

    /**
     * 是否使用 jakarta 包，用于解决 Spring Boot 2.X 和 3.X 的兼容性问题
     */
    @Setter
    private Boolean jakartaEnable;

    /**
     * 模板渲染器
     */
    private final TemplateRenderer templateRenderer;

    /**
     * 全局通用变量映射
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    @Autowired
    public DefaultCodeGenerator(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
        // 设置 javaxEnable，按照是否使用 JDK17 来判断
        this.jakartaEnable = SystemUtil.getJavaInfo().isJavaVersionAtLeast(1700); // 17.00 * 100
        initGlobalBindingMap();
    }

    @PostConstruct
    public void initGlobalBindingMap() {
        // 全局通用变量
        globalBindingMap.put("jakartaPackage", jakartaEnable ? "jakarta.persistence" : "javax.persistence");
        globalBindingMap.put("baseEntityClassName", "AbstractEntity");
        globalBindingMap.put("baseEntityPackage", "com.bone.metadata.sdk.domain.entity");
        globalBindingMap.put("baseVOClassName", "BaseVO");
        globalBindingMap.put("baseVOPackage", "com.bone.metadata.sdk.domain.vo");
        globalBindingMap.put("baseQueryClassName", "BaseQuery");
        globalBindingMap.put("baseQueryPackage", "com.bone.metadata.sdk.query");
        globalBindingMap.put("collectionUtilsClassName", "CollectionUtils");
        globalBindingMap.put("collectionUtilsPackage", "org.apache.commons.collections4");
        globalBindingMap.put("objectUtilsClassName", "ObjectUtils");
        globalBindingMap.put("objectUtilsPackage", "org.apache.commons.lang3");
        globalBindingMap.put("dateTimeFormatterClassName", "DateTimeFormatter");
        globalBindingMap.put("dateTimeFormatterPackage", "java.time.format");
        globalBindingMap.put("strUtil", "cn.hutool.core.util.StrUtil");
        globalBindingMap.put("dateUtil", "cn.hutool.core.date.DateUtil");
        // 保留原有必要的全局配置
        globalBindingMap.put("CommonResultClassName", "com.bone.core.model.ApiResponse");
        globalBindingMap.put("PageResultClassName", "com.bone.core.model.PageResult");
        globalBindingMap.put("PageParamClassName", "com.bone.core.model.PageParam");
        globalBindingMap.put("DictFormatClassName", "com.bone.tool.codegen.infrastructure.annotation.DictFormat");
        globalBindingMap.put("BaseDOClassName", "com.bone.core.domain.entity.AbstractEntity");
    }

    @Override
    public void generateCode(ZipOutputStream zipOut, CodegenTable codegenTable, Integer modelType) {
        try {
            // 避免直接调用不存在的getter方法
            List<CodegenColumn> columns = (List<CodegenColumn>) ReflectionUtil.getFieldValue(codegenTable, "columns");
            List<CodegenTable> subTables = (List<CodegenTable>) ReflectionUtil.getFieldValue(codegenTable, "subTables");
            DataSourceConfig dataSourceConfig = (DataSourceConfig) ReflectionUtil.getFieldValue(codegenTable, "dataSourceConfig");
            String groupId = (String) ReflectionUtil.getFieldValue(codegenTable, "groupId");
            
            List<List<CodegenColumn>> subColumnsList = new ArrayList<>();
            
            // 初始化子表列信息
            if (CollUtil.isNotEmpty(subTables)) {
                for (CodegenTable subTable : subTables) {
                    List<CodegenColumn> subColumns = (List<CodegenColumn>) ReflectionUtil.getFieldValue(subTable, "columns");
                    if (subColumns != null) {
                        subColumnsList.add(subColumns);
                    }
                }
            }
            
            // 初始化绑定参数
            Map<String, Object> params = initBindingMap(codegenTable, columns, subTables, subColumnsList, 
                    dataSourceConfig, groupId, modelType);
            
            // 生成主表代码
            generateMainTableCode(zipOut, params, modelType);
            
            // 生成子表代码
            if (CollUtil.isNotEmpty(subTables)) {
                generateSubTableCode(zipOut, params, modelType);
            }
        } catch (Exception e) {
            throw new RuntimeException("生成代码失败", e);
        }
    }

    @Override
    public void generateMainTableCode(ZipOutputStream zipOut, Map<String, Object> params, Integer modelType) {
        // 获取模板
        Map<String, String> templates = getTemplates(modelType);
        
        // 生成主表代码
        templates.forEach((templatePath, filePath) -> {
            try {
                String content = generateCode(templatePath, filePath, params);
                if (StringUtils.isNotEmpty(content)) {
                    zipOut.putNextEntry(new ZipEntry(filePath));
                    zipOut.write(content.getBytes("UTF-8"));
                    zipOut.closeEntry();
                }
            } catch (IOException e) {
                throw new RuntimeException("生成代码失败: " + filePath, e);
            }
        });
    }

    @Override
    public void generateSubTableCode(ZipOutputStream zipOut, Map<String, Object> params, Integer modelType) {
        List<CodegenTable> subTables = (List<CodegenTable>) params.get("subTables");
        if (CollUtil.isEmpty(subTables)) {
            return;
        }
        
        // 获取模板
        Map<String, String> templates = getTemplates(modelType);
        
        // 逐个生成子表代码
        for (int i = 0; i < subTables.size(); i++) {
            params.put("subIndex", i);
            templates.forEach((templatePath, filePath) -> {
                // 过滤主子表相关的模板
                CodegenTable mainTable = (CodegenTable) params.get("table");
                Integer templateType = ReflectionUtil.getIntegerFieldValue(mainTable, "templateType");
                if (shouldSkipSubTableTemplate(templatePath, templateType)) {
                    return;
                }
                
                try {
                    String content = generateCode(templatePath, filePath, params);
                    if (StringUtils.isNotEmpty(content)) {
                        zipOut.putNextEntry(new ZipEntry(filePath));
                        zipOut.write(content.getBytes("UTF-8"));
                        zipOut.closeEntry();
                    }
                } catch (IOException e) {
                    throw new RuntimeException("生成子表代码失败: " + filePath, e);
                }
            });
        }
        params.remove("subIndex");
    }

    @Override
    public TemplateRenderer getTemplateRenderer() {
        return templateRenderer;
    }
    
    // 添加缺失的方法
    public Map<String, String> generateCode(List<CodegenColumn> columns, List<CodegenTable> subTables, 
                                          DataSourceConfig dataSourceConfig, String groupId, Integer modelType) {
        CodegenTable table = new CodegenTable();
        return execute(table, columns, subTables, new ArrayList<>(), dataSourceConfig, groupId, modelType);
    }
    
    public Map<String, String> generateSubCode(List<CodegenColumn> columns, CodegenTable subTable, Integer modelType) {
        return execute(subTable, columns, null, null, null, null, modelType);
    }
    
    // 添加execute方法作为内部实现
    private Map<String, String> execute(CodegenTable table, List<CodegenColumn> columns, List<CodegenTable> subTables,
                                     List<List<CodegenColumn>> subColumnsList, DataSourceConfig dataSourceConfig,
                                     String groupId, Integer modelType) {
        Map<String, String> result = new HashMap<>();
        try {
            // 初始化绑定参数
            Map<String, Object> params = initBindingMap(table, columns, subTables, subColumnsList,
                    dataSourceConfig, groupId, modelType);
            
            // 获取模板
            Map<String, String> templates = getTemplates(modelType);
            
            // 生成代码
            templates.forEach((templatePath, filePath) -> {
                try {
                    String content = generateCode(templatePath, filePath, params);
                    if (StringUtils.isNotEmpty(content)) {
                        result.put(filePath, content);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("生成代码失败: " + filePath, e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("执行代码生成失败", e);
        }
        return result;
    }

    private String generateCode(String templatePath, String filePath, Map<String, Object> params) {
        // 格式化文件路径
        filePath = formatFilePath(filePath, params);
        // 渲染模板
        String content = templateRenderer.render(templatePath, params);
        // 格式化代码
        return prettyCode(content);
    }

    private boolean shouldSkipSubTableTemplate(String templatePath, Integer templateType) {
        if (templatePath.contains("_normal")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_NORMAL.getType())) {
            return true;
        }
        if (templatePath.contains("_erp")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_ERP.getType())) {
            return true;
        }
        if (templatePath.contains("_inner")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_INNER.getType())) {
            return true;
        }
        return false;
    }

    private Map<String, String> getTemplates(Integer modelType) {
        Map<String, String> templates = new LinkedHashMap<>();
        ModelTypeEnum modelTypeEnum = ModelTypeEnum.valueOf(modelType);
        templates.putAll(modelTypeEnum.getJavaTemplates(modelTypeEnum.getName()));
        templates.putAll(modelTypeEnum.getConfigTemplates(modelTypeEnum.getName()));
        return templates;
    }

    private Map<String, Object> initBindingMap(CodegenTable table, List<CodegenColumn> columns, 
                                             List<CodegenTable> subTables, List<List<CodegenColumn>> subColumnsList, 
                                             DataSourceConfig dataSourceConfigDO, String groupId, Integer modelType) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("modelType", modelType);
        bindingMap.put("groupId", groupId);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        
        // 查找主键字段
        CodegenColumn primaryColumn = null;
        for (CodegenColumn column : columns) {
            if (ReflectionUtil.getBooleanFieldValue(column, "primaryKey")) {
                primaryColumn = column;
                break;
            }
        }
        bindingMap.put("primaryColumn", primaryColumn);
        
        Integer scene = ReflectionUtil.getIntegerFieldValue(table, "scene");
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(scene));
        
        String basePackage = ReflectionUtil.getStringFieldValue(table, "packageName");
        if (StringUtils.isNotBlank(basePackage) && basePackage.contains(".")) {
            basePackage = basePackage.substring(basePackage.lastIndexOf('.') + 1, basePackage.length());
        }
        bindingMap.put("basePackage", basePackage);
        
        // className 相关
        String className = ReflectionUtil.getStringFieldValue(table, "className");
        String moduleName = ReflectionUtil.getStringFieldValue(table, "moduleName");
        String simpleClassName = removePrefix(className, upperFirst(moduleName));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName));
        bindingMap.put("classNameVar", lowerFirst(simpleClassName));
        bindingMap.put("modelNameVar", upperFirst(toPascalCase(moduleName)));
        
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        bindingMap.put("permissionPrefix", moduleName + ":" + simpleClassNameStrikeCase);
        
        // 数据源信息
        bindingMap.put("dataSourceUrl", ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "url"));
        bindingMap.put("dataSourceUsername", ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "username"));
        bindingMap.put("dataSourcePassword", ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "password"));
        
        // 树表逻辑
        initTreeTableBinding(bindingMap, table, columns);
        
        // 主子表逻辑
        initMasterSlaveBinding(bindingMap, table, subTables, subColumnsList);
        
        return bindingMap;
    }

    private void initTreeTableBinding(Map<String, Object> bindingMap, CodegenTable table, List<CodegenColumn> columns) {
        Integer templateType = ReflectionUtil.getIntegerFieldValue(table, "templateType");
        if (templateType != null && templateType.equals(CodegenTemplateTypeEnum.TREE.getType())) {
            Long treeParentColumnId = ReflectionUtil.getLongFieldValue(table, "treeParentColumnId");
            CodegenColumn treeParentColumn = findColumnById(columns, treeParentColumnId);
            bindingMap.put("treeParentColumn", treeParentColumn);
            if (treeParentColumn != null) {
                bindingMap.put("treeParentColumn_javaField_underlineCase", 
                        toUnderlineCase(ReflectionUtil.getStringFieldValue(treeParentColumn, "javaField")));
            }
            
            Long treeNameColumnId = ReflectionUtil.getLongFieldValue(table, "treeNameColumnId");
            CodegenColumn treeNameColumn = findColumnById(columns, treeNameColumnId);
            bindingMap.put("treeNameColumn", treeNameColumn);
            if (treeNameColumn != null) {
                bindingMap.put("treeNameColumn_javaField_underlineCase", 
                        toUnderlineCase(ReflectionUtil.getStringFieldValue(treeNameColumn, "javaField")));
            }
        }
    }

    private void initMasterSlaveBinding(Map<String, Object> bindingMap, CodegenTable table, 
                                       List<CodegenTable> subTables, List<List<CodegenColumn>> subColumnsList) {
        if (CollUtil.isNotEmpty(subTables)) {
            bindingMap.put("subTables", subTables);
            bindingMap.put("subColumnsList", subColumnsList);
            
            List<CodegenColumn> subPrimaryColumns = new ArrayList<>();
            List<CodegenColumn> subJoinColumns = new ArrayList<>();
            List<String> subJoinColumnStrikeCases = new ArrayList<>();
            List<String> subSimpleClassNames = new ArrayList<>();
            List<String> subClassNameVars = new ArrayList<>();
            List<String> simpleClassNameUnderlineCases = new ArrayList<>();
            List<String> subSimpleClassNameStrikeCases = new ArrayList<>();
            
            for (int i = 0; i < subTables.size(); i++) {
                CodegenTable subTable = subTables.get(i);
                List<CodegenColumn> subColumns = subColumnsList.get(i);
                
                // 找到子表主键
                CodegenColumn pkColumn = findPrimaryColumn(subColumns);
                subPrimaryColumns.add(pkColumn);
                
                // 找到关联列
                Long subJoinColumnId = ReflectionUtil.getLongFieldValue(subTable, "subJoinColumnId");
                CodegenColumn subColumn = findColumnById(subColumns, subJoinColumnId);
                subJoinColumns.add(subColumn);
                
                if (subColumn != null) {
                    subJoinColumnStrikeCases.add(toSymbolCase(ReflectionUtil.getStringFieldValue(subColumn, "javaField"), '-'));
                } else {
                    subJoinColumnStrikeCases.add("");
                }
                
                // className 相关
                String subClassName = ReflectionUtil.getStringFieldValue(subTable, "className");
                String subModuleName = ReflectionUtil.getStringFieldValue(subTable, "moduleName");
                String subSimpleClassName = removePrefix(subClassName, upperFirst(subModuleName));
                subSimpleClassNames.add(subSimpleClassName);
                simpleClassNameUnderlineCases.add(toUnderlineCase(subSimpleClassName));
                subClassNameVars.add(lowerFirst(subSimpleClassName));
                subSimpleClassNameStrikeCases.add(toSymbolCase(subSimpleClassName, '-'));
            }
            
            bindingMap.put("subPrimaryColumns", subPrimaryColumns);
            bindingMap.put("subJoinColumns", subJoinColumns);
            bindingMap.put("subJoinColumn_strikeCases", subJoinColumnStrikeCases);
            bindingMap.put("subSimpleClassNames", subSimpleClassNames);
            bindingMap.put("simpleClassNameUnderlineCases", simpleClassNameUnderlineCases);
            bindingMap.put("subClassNameVars", subClassNameVars);
            bindingMap.put("subSimpleClassName_strikeCases", subSimpleClassNameStrikeCases);
        }
    }

    private CodegenColumn findPrimaryColumn(List<CodegenColumn> columns) {
        for (CodegenColumn column : columns) {
            if (Boolean.TRUE.equals(ReflectionUtil.getBooleanFieldValue(column, "primaryKey"))) {
                return column;
            }
        }
        return null;
    }

    private CodegenColumn findColumnById(List<CodegenColumn> columns, Long columnId) {
        if (columnId == null) {
            return null;
        }
        for (CodegenColumn column : columns) {
            if (Objects.equals(ReflectionUtil.getLongFieldValue(column, "id"), columnId)) {
                return column;
            }
        }
        return null;
    }

    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${basePackage}",
                getStr(bindingMap, "basePackage").replaceAll("\\.", "/"));
        filePath = StrUtil.replace(filePath, "${classNameVar}",
                getStr(bindingMap, "classNameVar"));
        filePath = StrUtil.replace(filePath, "${modelNameVar}",
                getStr(bindingMap, "modelNameVar"));
        filePath = StrUtil.replace(filePath, "${simpleClassName}",
                getStr(bindingMap, "simpleClassName"));
        
        // sceneEnum 替换
        CodegenSceneEnum sceneEnum = (CodegenSceneEnum) bindingMap.get("sceneEnum");
        try {
            filePath = StrUtil.replace(filePath, "${sceneEnum.prefixClass}", ReflectionUtil.getStringFieldValue(sceneEnum, "prefixClass"));
            filePath = StrUtil.replace(filePath, "${sceneEnum.basePackage}", ReflectionUtil.getStringFieldValue(sceneEnum, "basePackage"));
            filePath = StrUtil.replace(filePath, "${sceneEnum.scene}", String.valueOf(ReflectionUtil.getIntegerFieldValue(sceneEnum, "scene")));
        } catch (Exception e) {
            // 使用默认值防止编译错误
            filePath = StrUtil.replace(filePath, "${sceneEnum.prefixClass}", "");
            filePath = StrUtil.replace(filePath, "${sceneEnum.basePackage}", "admin");
            filePath = StrUtil.replace(filePath, "${sceneEnum.scene}", "1");
        }
        
        // table 相关替换
        CodegenTable table = (CodegenTable) bindingMap.get("table");
        filePath = StrUtil.replace(filePath, "${table.moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
        filePath = StrUtil.replace(filePath, "${table.packageName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packageName")));
        filePath = StrUtil.replace(filePath, "${table.businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
        filePath = StrUtil.replace(filePath, "${table.className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className")));
        
        // 普通变量替换
        filePath = StrUtil.replace(filePath, "${moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
        filePath = StrUtil.replace(filePath, "${packageName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packageName")));
        filePath = StrUtil.replace(filePath, "${businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
        filePath = StrUtil.replace(filePath, "${className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className")));
        
        // 子表相关替换
        Integer subIndex = (Integer) bindingMap.get("subIndex");
        if (subIndex != null) {
            CodegenTable subTable = ((List<CodegenTable>) bindingMap.get("subTables")).get(subIndex);
            filePath = StrUtil.replace(filePath, "${subTable.moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "moduleName")));
            filePath = StrUtil.replace(filePath, "${subTable.businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "businessName")));
            filePath = StrUtil.replace(filePath, "${subTable.className}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "className")));
            filePath = StrUtil.replace(filePath, "${subSimpleClassName}",
                    ((List<String>) bindingMap.get("subSimpleClassNames")).get(subIndex));
        }
        
        return filePath;
    }

    private String prettyCode(String content) {
        // Vue 界面：去除字段后面多余的 , 逗号
        content = content.replaceAll(",\\n}", "\\n}").replaceAll(",\\n  }", "\\n  }");
        
        // Vue 界面：去除多的 dateFormatter
        if (StrUtil.count(content, "dateFormatter") == 1) {
            content = removeLineContains(content, "dateFormatter");
        }
        
        // Vue2 界面：修正 $refs
        if (StrUtil.count(content, "this.refs") >= 1) {
            content = content.replace("this.refs", "this.$refs");
        }
        
        // Vue 界面：去除未使用的 dict 相关
        if (StrUtil.count(content, "getIntDictOptions") == 1) {
            content = content.replace("getIntDictOptions, ", "");
        }
        if (StrUtil.count(content, "getStrDictOptions") == 1) {
            content = content.replace("getStrDictOptions, ", "");
        }
        if (StrUtil.count(content, "getBoolDictOptions") == 1) {
            content = content.replace("getBoolDictOptions, ", "");
        }
        if (StrUtil.count(content, "DICT_TYPE.") == 0) {
            content = removeLineContains(content, "DICT_TYPE");
        }
        
        return content;
    }

    private static String removeLineContains(String content, String sequence) {
        if (StrUtil.isEmpty(content) || StrUtil.isEmpty(sequence)) {
            return content;
        }
        return java.util.Arrays.stream(content.split("\\n"))
                .filter(line -> !line.contains(sequence))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 统一将 '-' 和 '_' 都作为分隔符
        String[] parts = input.replaceAll("-", "_").split("_");

        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                // 每个单词首字母大写，其余小写
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }
}