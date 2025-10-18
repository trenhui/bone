package com.bone.tool.codegen.domain.service.generator;

import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.enums.CodegenSceneEnum;
import com.bone.tool.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;
import com.bone.tool.codegen.domain.service.renderer.TemplateRenderer;
import com.bone.tool.codegen.infrastructure.util.ReflectionUtil;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Objects;

import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


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
        // 设置 jakartaEnable，按照是否使用 JDK17 来判断
        String javaVersion = System.getProperty("java.version");
        // 解析Java版本号，如 "17.0.1" -> 17
        int majorVersion = 8; // 默认JDK8
        if (javaVersion.startsWith("1.")) {
            // 对于JDK 1.8及以下版本
            majorVersion = Integer.parseInt(javaVersion.substring(2, 3));
        } else {
            // 对于JDK 9及以上版本
            int dotIndex = javaVersion.indexOf('.');
            if (dotIndex > 0) {
                majorVersion = Integer.parseInt(javaVersion.substring(0, dotIndex));
            } else {
                // 处理纯数字版本号
                try {
                    majorVersion = Integer.parseInt(javaVersion);
                } catch (NumberFormatException e) {
                    // 默认使用8
                }
            }
        }
        this.jakartaEnable = majorVersion >= 17;
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
        globalBindingMap.put("dateTimeFormatterClassName", "DateTimeFormatter");
        globalBindingMap.put("dateTimeFormatterPackage", "java.time.format");
        // 使用Java标准库
        globalBindingMap.put("strUtil", "java.util.Objects");
        globalBindingMap.put("dateUtil", "java.time.LocalDateTime");
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
            // 注意：CodegenTable中没有定义getColumns、getSubTables等方法，需要从外部传入这些数据
            // 这里暂时保留反射调用，但会在其他地方优化反射使用
            List<CodegenColumn> columns = (List<CodegenColumn>) ReflectionUtil.getFieldValue(codegenTable, "columns");
            List<CodegenTable> subTables = (List<CodegenTable>) ReflectionUtil.getFieldValue(codegenTable, "subTables");
            Datasource dataSourceConfig = (Datasource) ReflectionUtil.getFieldValue(codegenTable, "dataSourceConfig");
            String groupId = (String) ReflectionUtil.getFieldValue(codegenTable, "groupId");
            
            List<List<CodegenColumn>> subColumnsList = new ArrayList<>();
            
            // 初始化子表列信息
            if (subTables != null && !subTables.isEmpty()) {
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
            if (subTables != null && !subTables.isEmpty()) {
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
                if (content != null && !content.isEmpty()) {
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
        if (subTables == null || subTables.isEmpty()) {
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
                    if (content != null && !content.isEmpty()) {
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
                                          Datasource dataSourceConfig, String groupId, Integer modelType) {
        CodegenTable table = new CodegenTable();
        return execute(table, columns, subTables, new ArrayList<>(), dataSourceConfig, groupId, modelType);
    }
    
    public Map<String, String> generateSubCode(List<CodegenColumn> columns, CodegenTable subTable, Integer modelType) {
        return execute(subTable, columns, null, null, null, null, modelType);
    }
    
    // 添加execute方法作为内部实现
    private Map<String, String> execute(CodegenTable table, List<CodegenColumn> columns, List<CodegenTable> subTables,
                                     List<List<CodegenColumn>> subColumnsList, Datasource dataSourceConfig,
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
                if (content != null && !content.isEmpty()) {
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
                && !Objects.equals(templateType, CodegenTemplateTypeEnum.MASTER_NORMAL.getType())) {
            return true;
        }
        if (templatePath.contains("_erp")
                && !Objects.equals(templateType, CodegenTemplateTypeEnum.MASTER_ERP.getType())) {
            return true;
        }
        if (templatePath.contains("_inner")
                && !Objects.equals(templateType, CodegenTemplateTypeEnum.MASTER_INNER.getType())) {
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
                                             Datasource dataSourceConfig, String groupId, Integer modelType) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("modelType", modelType);
        bindingMap.put("groupId", groupId);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        
        // 查找主键字段（使用反射获取）
        CodegenColumn primaryColumn = null;
        for (CodegenColumn column : columns) {
            Object primaryKeyObj = ReflectionUtil.getFieldValue(column, "primaryKey");
            if (Boolean.TRUE.equals(primaryKeyObj)) {
                primaryColumn = column;
                break;
            }
        }
        bindingMap.put("primaryColumn", primaryColumn);
        
        // 使用反射获取字段值
        Integer scene = (Integer) ReflectionUtil.getFieldValue(table, "scene");
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(scene));
        
        String basePackage = (String) ReflectionUtil.getFieldValue(table, "packageName");
        if (basePackage != null && !basePackage.trim().isEmpty() && basePackage.contains(".")) {
            basePackage = basePackage.substring(basePackage.lastIndexOf('.') + 1, basePackage.length());
        }
        bindingMap.put("basePackage", basePackage);
        
        // className 相关
        String className = String.valueOf(ReflectionUtil.getStringFieldValue(table, "className"));
        String moduleName = String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName"));
        String simpleClassName = removePrefix(className, upperFirst(moduleName));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName));
        bindingMap.put("classNameVar", lowerFirst(simpleClassName));
        bindingMap.put("modelNameVar", upperFirst(toPascalCase(moduleName)));
        
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        bindingMap.put("permissionPrefix", moduleName + ":" + simpleClassNameStrikeCase);
        
        // 数据源信息
        bindingMap.put("dataSourceConfig", dataSourceConfig);
        // 注意：DataSourceConfig可能没有getter方法，暂时保留反射调用
        bindingMap.put("dataSourceUrl", ReflectionUtil.getStringFieldValue(dataSourceConfig, "url"));
        bindingMap.put("dataSourceUsername", ReflectionUtil.getStringFieldValue(dataSourceConfig, "username"));
        bindingMap.put("dataSourcePassword", ReflectionUtil.getStringFieldValue(dataSourceConfig, "password"));
        bindingMap.put("dbType", getDbType(ReflectionUtil.getStringFieldValue(dataSourceConfig, "url")));
        
        // 树表逻辑
        initTreeTableBinding(bindingMap, table, columns);
        
        // 主子表逻辑
        initMasterSlaveBinding(bindingMap, table, subTables, subColumnsList);
        
        return bindingMap;
    }

    /**
     * 根据数据库URL获取数据库类型
     */
    private String getDbType(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "unknown";
        }
        if (jdbcUrl.contains("mysql")) {
            return "mysql";
        } else if (jdbcUrl.contains("oracle")) {
            return "oracle";
        } else if (jdbcUrl.contains("postgresql")) {
            return "postgresql";
        } else if (jdbcUrl.contains("sqlserver")) {
            return "sqlserver";
        } else if (jdbcUrl.contains("sqlite")) {
            return "sqlite";
        } else {
            return "other";
        }
    }
    
    private void initTreeTableBinding(Map<String, Object> bindingMap, CodegenTable table, List<CodegenColumn> columns) {
        // 使用反射获取字段值
        Integer templateType = ReflectionUtil.getIntegerFieldValue(table, "templateType");
        if (templateType != null && templateType.equals(CodegenTemplateTypeEnum.TREE.getType())) {
            Long treeParentColumnId = ReflectionUtil.getLongFieldValue(table, "treeParentColumnId");
            CodegenColumn treeParentColumn = findColumnById(columns, treeParentColumnId);
            bindingMap.put("treeParentColumn", treeParentColumn);
            if (treeParentColumn != null) {
                bindingMap.put("treeParentColumn_javaField_underlineCase", 
                        toUnderlineCase(String.valueOf(ReflectionUtil.getStringFieldValue(treeParentColumn, "javaField"))));
            }
            
            Long treeNameColumnId = ReflectionUtil.getLongFieldValue(table, "treeNameColumnId");
            CodegenColumn treeNameColumn = findColumnById(columns, treeNameColumnId);
            bindingMap.put("treeNameColumn", treeNameColumn);
            if (treeNameColumn != null) {
                bindingMap.put("treeNameColumn_javaField_underlineCase", 
                        toUnderlineCase(String.valueOf(ReflectionUtil.getStringFieldValue(treeNameColumn, "javaField"))));
            }
        }
    }

    private void initMasterSlaveBinding(Map<String, Object> bindingMap, CodegenTable table, 
                                       List<CodegenTable> subTables, List<List<CodegenColumn>> subColumnsList) {
        if (subTables != null && !subTables.isEmpty()) {
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
                
                // 找到关联列 - 使用反射
                Long subJoinColumnId = ReflectionUtil.getLongFieldValue(subTable, "subJoinColumnId");
                CodegenColumn subColumn = findColumnById(subColumns, subJoinColumnId);
                subJoinColumns.add(subColumn);
                
                if (subColumn != null) {
                    // 使用getter方法
                    subJoinColumnStrikeCases.add(toSymbolCase(String.valueOf(ReflectionUtil.getStringFieldValue(subColumn, "javaField")), '-'));
                } else {
                    subJoinColumnStrikeCases.add("");
                }
                
                // className 相关 - 使用反射
                String subClassName = String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "className"));
                String subModuleName = String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "moduleName"));
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
        // 安全地从Map中获取值并转换为字符串
        String basePackage = bindingMap.get("basePackage") != null ? bindingMap.get("basePackage").toString() : "";
        String classNameVar = bindingMap.get("classNameVar") != null ? bindingMap.get("classNameVar").toString() : "";
        String modelNameVar = bindingMap.get("modelNameVar") != null ? bindingMap.get("modelNameVar").toString() : "";
        String simpleClassName = bindingMap.get("simpleClassName") != null ? bindingMap.get("simpleClassName").toString() : "";
        
        filePath = filePath.replace("${basePackage}", basePackage.replaceAll("\\.", "/"));
        filePath = filePath.replace("${classNameVar}", classNameVar);
        filePath = filePath.replace("${modelNameVar}", modelNameVar);
        filePath = filePath.replace("${simpleClassName}", simpleClassName);
        
        // sceneEnum 替换
        CodegenSceneEnum sceneEnum = (CodegenSceneEnum) bindingMap.get("sceneEnum");
        try {
            filePath = filePath.replace("${sceneEnum.prefixClass}", ReflectionUtil.getStringFieldValue(sceneEnum, "prefixClass"));
            filePath = filePath.replace("${sceneEnum.basePackage}", ReflectionUtil.getStringFieldValue(sceneEnum, "basePackage"));
            filePath = filePath.replace("${sceneEnum.scene}", String.valueOf(ReflectionUtil.getIntegerFieldValue(sceneEnum, "scene")));
        } catch (Exception e) {
            // 使用默认值防止编译错误
            filePath = filePath.replace("${sceneEnum.prefixClass}", "");
            filePath = filePath.replace("${sceneEnum.basePackage}", "admin");
            filePath = filePath.replace("${sceneEnum.scene}", "1");
        }
        
        // table 相关替换
        CodegenTable table = (CodegenTable) bindingMap.get("table");
        filePath = filePath.replace("${table.moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
        filePath = filePath.replace("${table.packageName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packageName")));
        filePath = filePath.replace("${table.businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
        filePath = filePath.replace("${table.className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className")));
        
        // 普通变量替换
        filePath = filePath.replace("${moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
        filePath = filePath.replace("${packageName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packageName")));
        filePath = filePath.replace("${businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
        filePath = filePath.replace("${className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className")));
        
        // 子表相关替换
        Integer subIndex = (Integer) bindingMap.get("subIndex");
        if (subIndex != null) {
            CodegenTable subTable = ((List<CodegenTable>) bindingMap.get("subTables")).get(subIndex);
            filePath = filePath.replace("${subTable.moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "moduleName")));
            filePath = filePath.replace("${subTable.businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "businessName")));
            filePath = filePath.replace("${subTable.className}", String.valueOf(ReflectionUtil.getStringFieldValue(subTable, "className")));
            filePath = filePath.replace("${subSimpleClassName}",
                    ((List<String>) bindingMap.get("subSimpleClassNames")).get(subIndex));
        }
        
        return filePath;
    }

    private String prettyCode(String content) {
        // Vue 界面：去除字段后面多余的 , 逗号
        content = content.replaceAll(",\\n}", "\\n}").replaceAll(",\\n  }", "\\n  }");
        
        // Vue 界面：去除多的 dateFormatter
        if (countOccurrencesOf(content, "dateFormatter") == 1) {
            content = removeLineContains(content, "dateFormatter");
        }
        
        // Vue2 界面：修正 $refs
        if (countOccurrencesOf(content, "this.refs") >= 1) {
            content = content.replace("this.refs", "this.$refs");
        }
        
        // Vue 界面：去除未使用的 dict 相关
        if (countOccurrencesOf(content, "getIntDictOptions") == 1) {
            content = content.replace("getIntDictOptions, ", "");
        }
        if (countOccurrencesOf(content, "getStrDictOptions") == 1) {
            content = content.replace("getStrDictOptions, ", "");
        }
        if (countOccurrencesOf(content, "getBoolDictOptions") == 1) {
            content = content.replace("getBoolDictOptions, ", "");
        }
        if (countOccurrencesOf(content, "DICT_TYPE.") == 0) {
            content = removeLineContains(content, "DICT_TYPE");
        }
        
        return content;
    }
    
    // 自定义实现字符串计数方法
    private int countOccurrencesOf(String str, String sub) {
        if (str == null || sub == null || str.isEmpty() || sub.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }
        return count;
    }

    private static String removeLineContains(String content, String sequence) {
        if (content == null || content.isEmpty() || sequence == null || sequence.isEmpty()) {
            return content;
        }
        return java.util.Arrays.stream(content.split("\\n"))
                .filter(line -> !line.contains(sequence))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 将字符串转为使用指定符号分隔的格式
     */
    private String toSymbolCase(String str, char symbol) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                result.append(symbol);
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
    
    /**
     * 将字符串首字母转为大写
     */
    private String upperFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 将字符串首字母转为小写
     */
    private String lowerFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
    
    /**
     * 将驼峰命名转为下划线命名
     */
    private String toUnderlineCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return toSymbolCase(str, '_');
    }
    
    /**
     * 转为首字母大写的驼峰命名法（Pascal Case）
     */
    public String toPascalCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 移除字符串前缀
     */
    private String removePrefix(String str, String prefix) {
        if (str == null || prefix == null || !str.startsWith(prefix)) {
            return str;
        }
        return str.substring(prefix.length());
    }

}