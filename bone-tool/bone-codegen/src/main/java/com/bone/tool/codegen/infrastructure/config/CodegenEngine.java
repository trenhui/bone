package com.bone.tool.codegen.infrastructure.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.engine.velocity.VelocityEngine;
import cn.hutool.system.SystemUtil;
import com.bone.core.model.ApiResponse;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.enums.CodegenSceneEnum;
import com.bone.tool.codegen.domain.enums.CodegenTemplateTypeEnum;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;

import com.bone.tool.codegen.infrastructure.annotation.DictFormat;
import com.bone.tool.codegen.infrastructure.util.ReflectionUtil;
import com.bone.core.model.PageParam;
import com.bone.core.model.PageResult;
import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.hutool.core.map.MapUtil.getStr;
import static cn.hutool.core.text.CharSequenceUtil.*;

/**
 * 代码生成的引擎，用于具体生成代码
 * 目前基于  模板引擎实现
 *
 * 考虑到 Java 模板引擎的框架非常多，Freemarker、Velocity、Thymeleaf 等等，所以我们采用 hutool 封装的 {@link cn.hutool.extra.template.Template} 抽象
 *
 * @author bone-team
 */
@Component
public class CodegenEngine {
//    @Resource
//    private CodegenProperties codegenProperties;

    /**
     * 是否使用 jakarta 包，用于解决 Spring Boot 2.X 和 3.X 的兼容性问题
     *
     * true  - 使用 jakarta.validation.constraints.*
     * false - 使用 javax.validation.constraints.*
     */
    @Setter // 允许设置的原因，是因为单测需要手动改变
    private Boolean jakartaEnable;

    /**
     * 模板引擎，由 hutool 实现
     */
    private final TemplateEngine templateEngine;
    /**
     * 全局通用变量映射
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    public CodegenEngine() {
        // 初始化 TemplateEngine 属性
        TemplateConfig config = new TemplateConfig();
        config.setResourceMode(TemplateConfig.ResourceMode.CLASSPATH);
        this.templateEngine = new VelocityEngine(config);
        // 设置 javaxEnable，按照是否使用 JDK17 来判断
        this.jakartaEnable = SystemUtil.getJavaInfo().isJavaVersionAtLeast(1700); // 17.00 * 100
    }

    @PostConstruct
    //@VisibleForTesting
    void initGlobalBindingMap() {
        // 全局配置
//        globalBindingMap.put("basePackage", codegenProperties.getBasePackage());
//        globalBindingMap.put("baseFrameworkPackage", codegenProperties.getBasePackage()
//                + '.' + "framework"); // 用于后续获取测试类的 package 地址
        globalBindingMap.put("jakartaPackage", jakartaEnable ? "jakarta" : "javax");
        // 全局 Java Bean

        globalBindingMap.put("CommonResultClassName", ApiResponse.class.getName());
        globalBindingMap.put("PageResultClassName", PageResult.class.getName());
        // VO 类，独有字段
        globalBindingMap.put("PageParamClassName", PageParam.class.getName());
        globalBindingMap.put("DictFormatClassName", DictFormat.class.getName());
        // DO 类，独有字段
        globalBindingMap.put("BaseDOClassName", AbstractEntity.class.getName());
        globalBindingMap.put("baseDOFields", CodegenBuilder.BASE_DO_FIELDS);
        globalBindingMap.put("QueryWrapperClassName", "com.bone.metadata.sdk.query.QueryWrapper");
        globalBindingMap.put("BaseMapperClassName", "com.bone.metadata.sdk.BaseRepository");
        // Util 工具类
//        globalBindingMap.put("ServiceExceptionUtilClassName", ServiceExceptionUtil.class.getName());
//        globalBindingMap.put("ExcelUtilsClassName", ExcelUtils.class.getName());
//        globalBindingMap.put("LocalDateTimeUtilsClassName", LocalDateTimeUtils.class.getName());
        globalBindingMap.put("ObjectUtilsClassName", "org.springframework.util.ObjectUtils");
//        globalBindingMap.put("DictConvertClassName", DictConvert.class.getName());
//        globalBindingMap.put("ApiAccessLogClassName", ApiAccessLog.class.getName());
//        globalBindingMap.put("OperateTypeEnumClassName", OperateTypeEnum.class.getName());
    }

    /**
     * 生成代码
     *
     * @param table 表定义
     * @param columns table 的字段定义数组
     * @param subTables 子表数组，当且仅当主子表时使用
     * @param subColumnsList subTables 的字段定义数组
     * @return 生成的代码，key 是路径，value 是对应代码
     */
    public Map<String, String> execute(CodegenTable table, List<CodegenColumn> columns,
                                       List<CodegenTable> subTables, List<List<CodegenColumn>> subColumnsList, DataSourceConfig dataSourceConfigDO, String groupId, Integer modelType) {
        // 1.1 初始化 bindMap 上下文
        Map<String, Object> bindingMap = initBindingMap(table, columns, subTables, subColumnsList,dataSourceConfigDO,groupId,modelType);
        // 1.2 获得模版
        Map<String, String> templates = getTemplates(modelType);

        // 2. 执行生成
        Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(templates.size()); // 有序
        templates.forEach((vmPath, filePath) -> {
            generateCode(result, vmPath, filePath, bindingMap);
        });
        return result;
    }

    /**
     * 生成代码
     * @param vmPath 模版路径
     * @param bindingMap 替换参数
     * @return
     */
    public String getSingleCode(String vmPath,Map<String,Object> bindingMap){
        return templateEngine.getTemplate(vmPath).render(bindingMap);
    }

    private void generateCode(Map<String, String> result, String vmPath,
                              String filePath, Map<String, Object> bindingMap) {
        filePath = formatFilePath(filePath, bindingMap);
        String content = templateEngine.getTemplate(vmPath).render(bindingMap);
        // 格式化代码
        content = prettyCode(content);
        result.put(filePath, content);
    }

    private void generateSubCode(CodegenTable table, List<CodegenTable> subTables,
                                 Map<String, String> result, String vmPath,
                                 String filePath, Map<String, Object> bindingMap) {
        // 没有子表，所以不生成
        if (CollUtil.isEmpty(subTables)) {
            return;
        }
        // 主子表的模式匹配。目的：过滤掉个性化的模版
        Integer templateType = ReflectionUtil.getIntegerFieldValue(table, "templateType");
        if (vmPath.contains("_normal")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_NORMAL.getType())) {
            return;
        }
        if (vmPath.contains("_erp")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_ERP.getType())) {
            return;
        }
        if (vmPath.contains("_inner")
                && ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.MASTER_INNER.getType())) {
            return;
        }

        // 逐个生成
        for (int i = 0; i < subTables.size(); i++) {
            bindingMap.put("subIndex", i);
            generateCode(result, vmPath, filePath, bindingMap);
        }
        bindingMap.remove("subIndex");
    }

    /**
     * 格式化生成后的代码
     *
     * 因为尽量让 vm 模版简单，所以统一的处理都在这个方法。
     * 如果不处理，Vue 的 Pretty 格式校验可能会报错
     *
     * @param content 格式化前的代码
     * @return 格式化后的代码
     */
    private String prettyCode(String content) {
        // Vue 界面：去除字段后面多余的 , 逗号，解决前端的 Pretty 代码格式检查的报错
        content = content.replaceAll(",\\n}", "\\n}").replaceAll(",\\n  }", "\\n  }");
        // Vue 界面：去除多的 dateFormatter，只有一个的情况下，说明没使用到
        if (StrUtil.count(content, "dateFormatter") == 1) {
            content = removeLineContains(content, "dateFormatter");
        }
        // Vue2 界面：修正 $refs
        if (StrUtil.count(content, "this.refs") >= 1) {
            content = content.replace("this.refs", "this.$refs");
        }
        // Vue 界面：去除多的 dict 相关，只有一个的情况下，说明没使用到
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
    
    /**
     * 移除字符串中，包含指定字符串的行
     *
     * @param content 字符串
     * @param sequence 包含的字符串
     * @return 移除后的字符串
     */
    private static String removeLineContains(String content, String sequence) {
        if (StrUtil.isEmpty(content) || StrUtil.isEmpty(sequence)) {
            return content;
        }
        return java.util.Arrays.stream(content.split("\\n"))
                .filter(line -> !line.contains(sequence))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * 将使用中划线（-）或下划线（_）分隔的字符串转换为 PascalCase（首字母大写的驼峰命名）
     * 例如：
     *   "insure-core"  -> "InsureCore"
     *   "insure_core"  -> "InsureCore"
     *   "hello_world-java" -> "HelloWorldJava"
     *
     * @param input 输入字符串
     * @return 转换后的 PascalCase 字符串
     */
    public  String toPascalCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 统一将 '-' 和 '_' 都作为分隔符，先替换成统一的分隔符（如 '_'），再按 '_' 分割
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

    private Map<String, Object> initBindingMap(CodegenTable table, List<CodegenColumn> columns,
                                               List<CodegenTable> subTables, List<List<CodegenColumn>> subColumnsList, DataSourceConfig dataSourceConfigDO, String groupId, Integer modelType) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("modelType", modelType);
        bindingMap.put("groupId",groupId);
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
        bindingMap.put("primaryColumn", primaryColumn); // 主键字段
        Integer scene = ReflectionUtil.getIntegerFieldValue(table, "scene");
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(scene));
        String basePackage = ReflectionUtil.getStringFieldValue(table, "packgeName");
        if(StringUtils.isNotBlank(basePackage)&&basePackage.contains(".")){
            basePackage = basePackage.substring(basePackage.lastIndexOf('.')+1,basePackage.length());
        }
        bindingMap.put("basePackage",basePackage);
        //bindingMap.put("groupId", );
        // className 相关
        // 去掉指定前缀，将 TestDictType 转换成 DictType. 因为在 create 等方法后，不需要带上 Test 前缀
        String className = ReflectionUtil.getStringFieldValue(table, "className");
        String moduleName = ReflectionUtil.getStringFieldValue(table, "moduleName");
        String simpleClassName = removePrefix(className, upperFirst(moduleName));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // 将 DictType 转换成 dict_type
        bindingMap.put("classNameVar", lowerFirst(simpleClassName)); // 将 DictType 转换成 dictType，用于变量
        bindingMap.put("modelNameVar",upperFirst(toPascalCase(moduleName)));
        // 将 DictType 转换成 dict-type
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        // permission 前缀
        bindingMap.put("permissionPrefix", moduleName + ":" + simpleClassNameStrikeCase);

        bindingMap.put("dataSourceUrl",ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "url"));
        bindingMap.put("dataSourceUsername",ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "username"));
        bindingMap.put("dataSourcePassword",ReflectionUtil.getStringFieldValue(dataSourceConfigDO, "password"));

        // 特殊：树表专属逻辑
          Integer templateType = ReflectionUtil.getIntegerFieldValue(table, "templateType");
          if (templateType != null && templateType.equals(CodegenTemplateTypeEnum.TREE.getType())) {
              CodegenColumn treeParentColumn = null;
              Long treeParentColumnId = ReflectionUtil.getLongFieldValue(table, "treeParentColumnId");
              for (CodegenColumn column : columns) {
                  if (Objects.equals(ReflectionUtil.getLongFieldValue(column, "id"), treeParentColumnId)) {
                      treeParentColumn = column;
                      break;
                  }
              }
              bindingMap.put("treeParentColumn", treeParentColumn);
              if (treeParentColumn != null) {
                  bindingMap.put("treeParentColumn_javaField_underlineCase", 
                      toUnderlineCase(ReflectionUtil.getStringFieldValue(treeParentColumn, "javaField")));
              }
              CodegenColumn treeNameColumn = null;
              Long treeNameColumnId = ReflectionUtil.getLongFieldValue(table, "treeNameColumnId");
              for (CodegenColumn column : columns) {
                  if (Objects.equals(ReflectionUtil.getLongFieldValue(column, "id"), treeNameColumnId)) {
                      treeNameColumn = column;
                      break;
                  }
              }
              bindingMap.put("treeNameColumn", treeNameColumn);
              if (treeNameColumn != null) {
                  bindingMap.put("treeNameColumn_javaField_underlineCase", 
                      toUnderlineCase(ReflectionUtil.getStringFieldValue(treeNameColumn, "javaField")));
              }
          }

        // 特殊：主子表专属逻辑
        if (CollUtil.isNotEmpty(subTables)) {
            // 创建 bindingMap
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
                List<CodegenColumn> subColumns = (List<CodegenColumn>) subColumnsList.get(i);
                CodegenColumn pkColumn = null;
            for (CodegenColumn column : subColumns) {
                if (Boolean.TRUE.equals(ReflectionUtil.getBooleanFieldValue(column, "primaryKey"))) {
                    pkColumn = column;
                    break;
                }
            }
            subPrimaryColumns.add(pkColumn); //
                CodegenColumn subColumn = null;
                Long subJoinColumnId = ReflectionUtil.getLongFieldValue(subTable, "subJoinColumnId");
            for (CodegenColumn column : subColumns) { // 关联的字段
                if (subJoinColumnId != null && Objects.equals(ReflectionUtil.getLongFieldValue(column, "id"), subJoinColumnId)) {
                    subColumn = column;
                    break;
                }
            }
                subJoinColumns.add(subColumn);
                if (subColumn != null) {
                    subJoinColumnStrikeCases.add(toSymbolCase(ReflectionUtil.getStringFieldValue(subColumn, "javaField"), '-')); // 将 DictType 转换成 dict-type
                } else {
                    subJoinColumnStrikeCases.add("");
                }
                // className 相关
                String subClassName = ReflectionUtil.getStringFieldValue(subTable, "className");
                String subModuleName = ReflectionUtil.getStringFieldValue(subTable, "moduleName");
                String subSimpleClassName = removePrefix(subClassName, upperFirst(subModuleName));
                subSimpleClassNames.add(subSimpleClassName);
                simpleClassNameUnderlineCases.add(toUnderlineCase(subSimpleClassName)); // 将 DictType 转换成 dict_type
                subClassNameVars.add(lowerFirst(subSimpleClassName)); // 将 DictType 转换成 dictType，用于变量
                subSimpleClassNameStrikeCases.add(toSymbolCase(subSimpleClassName, '-')); // 将 DictType 转换成 dict-type
            }
            bindingMap.put("subPrimaryColumns", subPrimaryColumns);
            bindingMap.put("subJoinColumns", subJoinColumns);
            bindingMap.put("subJoinColumn_strikeCases", subJoinColumnStrikeCases);
            bindingMap.put("subSimpleClassNames", subSimpleClassNames);
            bindingMap.put("simpleClassNameUnderlineCases", simpleClassNameUnderlineCases);
            bindingMap.put("subClassNameVars", subClassNameVars);
            bindingMap.put("subSimpleClassName_strikeCases", subSimpleClassNameStrikeCases);
        }
        return bindingMap;
    }

    private Map<String, String> getTemplates(Integer modelType) {
        Map<String, String> templates = new LinkedHashMap<>();
        ModelTypeEnum modelTypeEnum = ModelTypeEnum.valueOf(modelType);
        templates.putAll(modelTypeEnum.getJavaTemplates(modelTypeEnum.getName()));
        //templates.putAll(FRONT_TEMPLATES.row(frontType));
        templates.putAll(modelTypeEnum.getConfigTemplates(modelTypeEnum.getName()));
        return templates;
    }

    @SuppressWarnings("unchecked")
    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${basePackage}",
                getStr(bindingMap, "basePackage").replaceAll("\\.", "/"));
        filePath = StrUtil.replace(filePath, "${classNameVar}",
                getStr(bindingMap, "classNameVar"));
        filePath = StrUtil.replace(filePath, "${modelNameVar}",
                getStr(bindingMap, "modelNameVar"));
        filePath = StrUtil.replace(filePath, "${simpleClassName}",
                getStr(bindingMap, "simpleClassName"));
        // sceneEnum 包含的字段
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
        // table 包含的字段
          CodegenTable table = (CodegenTable) bindingMap.get("table");
          filePath = StrUtil.replace(filePath, "${table.moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
          filePath = StrUtil.replace(filePath, "${table.packgeName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packgeName")));
          filePath = StrUtil.replace(filePath, "${table.businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
          filePath = StrUtil.replace(filePath, "${table.className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className")));
          // 添加普通变量替换
          filePath = StrUtil.replace(filePath, "${moduleName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "moduleName")));
          filePath = StrUtil.replace(filePath, "${packgeName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "packgeName")));
          filePath = StrUtil.replace(filePath, "${businessName}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "businessName")));
          filePath = StrUtil.replace(filePath, "${className}", String.valueOf(ReflectionUtil.getStringFieldValue(table, "className"))); // DictType
        // 特殊：主子表专属逻辑
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


}
