package com.bone.infra.service.codegen.inner;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.engine.velocity.VelocityEngine;
import com.bone.base.core.exception.util.ServiceExceptionUtil;
import com.bone.base.core.pojo.CommonResult;
import com.bone.base.core.pojo.PageParam;
import com.bone.base.core.pojo.PageResult;
import com.bone.base.core.util.collection.CollectionUtils;
import com.bone.base.core.util.date.DateUtils;
import com.bone.base.core.util.object.ObjectUtils;
import com.bone.base.excel.core.annotations.DictFormat;
import com.bone.base.excel.core.convert.DictConvert;
import com.bone.base.excel.core.util.ExcelUtils;
import com.bone.base.mybatis.core.dataobject.BaseDO;
import com.bone.base.mybatis.core.mapper.BaseMapperX;
import com.bone.base.mybatis.core.query.LambdaQueryWrapperX;
import com.bone.base.operatelog.core.annotations.OperateLog;
import com.bone.base.operatelog.core.enums.OperateTypeEnum;
import com.bone.infra.dal.dataobject.codegen.CodegenColumnDO;
import com.bone.infra.dal.dataobject.codegen.CodegenTableDO;
import com.bone.infra.enums.codegen.CodegenSceneEnum;
import com.bone.infra.framework.codegen.config.CodegenProperties;
import com.google.common.collect.Maps;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.map.MapUtil.getStr;
import static cn.hutool.core.text.CharSequenceUtil.*;

/**
 * 代码生成的引擎，用于具体生成代码
 * 目前基于 {@link org.apache.velocity.app.Velocity} 模板引擎实现
 * <p>
 * 考虑到 Java 模板引擎的框架非常多，Freemarker、Velocity、Thymeleaf 等等，所以我们采用 hutool 封装的 {@link cn.hutool.extra.template.Template} 抽象
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class CodegenDDDEngine {

    /**
     * 模板配置
     * key：模板在 resources 的地址
     * value：生成的路径
     */
    private static final Map<String, String> TEMPLATES = MapUtil.<String, String>builder(new LinkedHashMap<>()) // 有序
            // Java module-biz Main
            .put(javaTemplatePath("application/ApplicationService"),
                    dddJavaModuleImplMainFilePath("application/${table.className}ApplicationService"))
            .put(javaTemplatePath("interfaces/rest/Controller"),
                    dddJavaModuleImplMainFilePath("interfaces/rest/${table.className}Controller"))
            .put(javaTemplatePath("interfaces/dto/DTO"),
                    dddJavaModuleImplMainFilePath("interfaces/dto/${table.className}DTO"))
            .put(javaTemplatePath("interfaces/convert/Convert"),
                    dddJavaModuleImplMainFilePath("interfaces/convert/${table.className}Convert"))
            .put(javaTemplatePath("domain/model/DomainModel"),
                    dddJavaModuleImplMainFilePath("domain/model/${table.className}"))
            .put(javaTemplatePath("domain/repository/Repository"),
                    dddJavaModuleImplMainFilePath("domain/repository/${table.className}Repository"))
            .put(javaTemplatePath("domain/service/impl/ServiceImpl"),
                    dddJavaModuleImplMainFilePath("domain/service/impl/${table.className}ServiceImpl"))
            .put(javaTemplatePath("domain/service/Service"),
                    dddJavaModuleImplMainFilePath("domain/service/${table.className}Service"))
            .put(javaTemplatePath("infrastructure/mq/mq"),
                    dddJavaModuleImplMainFilePath("infrastructure/mq/${table.moduleName}Producer"))
            .put(javaTemplatePath("infrastructure/mq/mq"),
                    dddJavaModuleImplMainFilePath("infrastructure/mq/${bigModuleNameVar}Producer"))
            .put(javaTemplatePath("infrastructure/config/security/JdbcAuditorConfig"),
                    dddJavaModuleImplMainFilePath("infrastructure/config/JdbcAuditorConfig"))

            .put(javaTemplatePath("infrastructure/config/security/SecurityConfiguration"),
                    dddJavaModuleImplMainFilePath("infrastructure/config/security/SecurityConfiguration"))
            .put(javaTemplatePath("Application"),
                    dddJavaModuleImplMainFilePath("${bigModuleNameVar}Application"))
            .put(javaTemplatePath("pom"),
                    dddModuleFilePath("pom.xml"))

            //antd pro
            .put(reactTemplatePath("api/api"),
                    reactFilePath("${table.moduleName}","services/${table.moduleName}/${classNameVar}.api.ts"))
            .put(reactTemplatePath("components/Form"),
                    reactFilePath("${table.moduleName}","pages/${table.moduleName}/${classNameVar}/components/${table.className}Form.tsx"))
            .put(reactTemplatePath("locales/zh-CN"),
                    reactFilePath("${table.moduleName}","locales/zh-CN/${table.moduleName}/${classNameVar}.ts"))
//            .put(reactTemplatePath("model/model"),
//                    reactFilePath("${table.moduleName}","model/${table.moduleName}.api.d.ts"))
            .put(reactTemplatePath("index"),
                    reactFilePath("${table.moduleName}","pages/${table.moduleName}/${classNameVar}/index.tsx"))
//            .put(reactTemplatePath("route"),
//                    reactFilePath("${table.moduleName}","routes.ts"))

            // Java module-biz Test
/*            .put(javaTemplatePath("test/serviceTest"),
                    javaModuleImplTestFilePath("service/${table.businessName}/${table.className}ServiceImplTest"))
            // Java module-api Main
            .put(javaTemplatePath("enums/errorcode"), javaModuleApiMainFilePath("enums/ErrorCodeConstants_手动操作"))
            // Vue2
            .put(vueTemplatePath("views/index.vue"),
                    vueFilePath("views/${table.moduleName}/${classNameVar}/index.vue"))
            .put(vueTemplatePath("api/api.js"),
                    vueFilePath("api/${table.moduleName}/${classNameVar}.js"))
            // Vue3
            .put(vue3TemplatePath("views/index.vue"),
                    vue3FilePath("views/${table.moduleName}/${classNameVar}/index.vue"))
            .put(vue3TemplatePath("views/data.ts"),
                    vue3FilePath("views/${table.moduleName}/${classNameVar}/${classNameVar}.data.ts"))
            .put(vue3TemplatePath("api/api.ts"),
                    vue3FilePath("api/${table.moduleName}/${classNameVar}/index.ts"))
            .put(vue3TemplatePath("api/types.ts"),
                    vue3FilePath("api/${table.moduleName}/${classNameVar}/types.ts"))
            // SQL
            .put("codegen/sql/sql.vm", "sql/sql.sql")
            .put("codegen/sql/h2.vm", "sql/h2.sql")*/
            .build();


    private static final Map<String, String> AggregationTEMPLATES = MapUtil.<String, String>builder(new LinkedHashMap<>()) // 有序
            //resources
            .put(resourcesTemplatePath("application"),
                    resourcesFilePath("${moduleName}","application.yaml"))
            .put(resourcesTemplatePath("application-local"),
                    resourcesFilePath("${moduleName}","application-local.yaml"))
            .put(resourcesTemplatePath("bootstrap"),
                    resourcesFilePath("${moduleName}","bootstrap.yaml"))
            .put(resourcesTemplatePath("bootstrap-local"),
                    resourcesFilePath("${moduleName}","bootstrap-local.yaml"))
            .put(resourcesTemplatePath("logback-spring"),
                    resourcesFilePath("${moduleName}","logback-spring.yaml"))



            //antd pro
            .put(reactTemplatePath("model/model"),
                    reactFilePath("${moduleName}","model/${moduleName}.api.d.ts"))
            .put(reactTemplatePath("route"),
                    reactFilePath("${moduleName}","routes.ts"))

            .build();

    @Resource
    private CodegenProperties codegenProperties;

    /**
     * 模板引擎，由 hutool 实现
     */
    private final TemplateEngine templateEngine;
    /**
     * 全局通用变量映射
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    public CodegenDDDEngine() {
        // 初始化 TemplateEngine 属性
        TemplateConfig config = new TemplateConfig();
        config.setResourceMode(TemplateConfig.ResourceMode.CLASSPATH);
        this.templateEngine = new VelocityEngine(config);
    }

    @PostConstruct
    private void initGlobalBindingMap() {
        // todo codegenProperties 初始化问题
        // 全局配置
        if (codegenProperties != null) {
            globalBindingMap.put("basePackage", codegenProperties.getBasePackage());
            globalBindingMap.put("baseFrameworkPackage", codegenProperties.getBasePackage()
                    + '.' + "framework"); // 用于后续获取测试类的 package 地址
        } else {
            globalBindingMap.put("basePackage", "com.bone");
            globalBindingMap.put("baseFrameworkPackage", "com.bone.framework");
            // 用于后续获取测试类的 package 地址
        }
        // 全局 Java Bean
        globalBindingMap.put("CommonResultClassName", CommonResult.class.getName());
        globalBindingMap.put("PageResultClassName", PageResult.class.getName());
        // VO 类，独有字段
        globalBindingMap.put("PageParamClassName", PageParam.class.getName());
        globalBindingMap.put("DictFormatClassName", DictFormat.class.getName());
        // DO 类，独有字段
        globalBindingMap.put("BaseDOClassName", BaseDO.class.getName());
        globalBindingMap.put("baseDOFields", CodegenBuilder.BASE_DO_FIELDS);
        globalBindingMap.put("QueryWrapperClassName", LambdaQueryWrapperX.class.getName());
        globalBindingMap.put("BaseMapperClassName", BaseMapperX.class.getName());
        // Util 工具类
        globalBindingMap.put("ServiceExceptionUtilClassName", ServiceExceptionUtil.class.getName());
        globalBindingMap.put("DateUtilsClassName", DateUtils.class.getName());
        globalBindingMap.put("ExcelUtilsClassName", ExcelUtils.class.getName());
        globalBindingMap.put("ObjectUtilsClassName", ObjectUtils.class.getName());
        globalBindingMap.put("DictConvertClassName", DictConvert.class.getName());
        globalBindingMap.put("OperateLogClassName", OperateLog.class.getName());
        globalBindingMap.put("OperateTypeEnumClassName", OperateTypeEnum.class.getName());
    }

    public Map<String, String> execute(CodegenTableDO table, List<CodegenColumnDO> columns) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        bindingMap.put("primaryColumn", CollectionUtils.findFirst(columns, CodegenColumnDO::getPrimaryKey)); // 主键字段
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(table.getScene()));

        // className 相关
        // 去掉指定前缀，将 TestDictType 转换成 DictType. 因为在 create 等方法后，不需要带上 Test 前缀
        String simpleClassName = removePrefix(table.getClassName(), upperFirst(table.getModuleName()));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // 将 DictType 转换成 dict_type
        bindingMap.put("classNameVar", lowerFirst(simpleClassName)); // 将 DictType 转换成 dictType，用于变量
        bindingMap.put("moduleNameVar", lowerFirst(table.getModuleName()));


        // 将 DictType 转换成 dict-type
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        // permission 前缀
        bindingMap.put("permissionPrefix", table.getModuleName() + ":" + simpleClassNameStrikeCase);

        // 执行生成
        final Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(TEMPLATES.size()); // 有序
        TEMPLATES.forEach((vmPath, filePath) -> {
            filePath = formatFilePath(filePath, bindingMap);
            String content = templateEngine.getTemplate(vmPath).render(bindingMap);
            result.put(filePath, content);
        });
        return result;
    }

    public Map<String, String> executeDDDTables(String moduleName,List<CodegenTableDO> tables) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("tables", tables);
        bindingMap.put("moduleName", moduleName);
        bindingMap.put("bigModuleNameVar", upperFirst(moduleName));
        bindingMap.put("moduleNameVar", lowerFirst(moduleName));

        // 执行生成
        final Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(AggregationTEMPLATES.size()); // 有序
        AggregationTEMPLATES.forEach((vmPath, filePath) -> {
            filePath = formatTablesFilePath(filePath, bindingMap);
            String content = templateEngine.getTemplate(vmPath).render(bindingMap);
            result.put(filePath, content);
        });
        return result;
    }

    public Map<String, String> executeDDD(CodegenTableDO table, List<CodegenColumnDO> columns) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        CodegenColumnDO primaryColumn = CollectionUtils.findFirst(columns, CodegenColumnDO::getPrimaryKey);
        bindingMap.put("primaryColumn", primaryColumn); // 主键字段
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(table.getScene()));
        CodegenColumnDO uniqueKeyColumn = columns.stream().filter(o -> o.getJavaField().equalsIgnoreCase("code"))
                .findFirst().orElse(primaryColumn);

        bindingMap.put("uniqueKeyColumn", uniqueKeyColumn); //唯一字段

        //模块相关
        if (StringUtils.hasText(table.getBasePackage())) {
            bindingMap.put("basePackage", table.getBasePackage());
        }

        if (StringUtils.hasText(table.getModuleName())) {
            bindingMap.put("moduleName", table.getModuleName());
        }

        // className 相关
        // 去掉指定前缀，将 TestDictType 转换成 DictType. 因为在 create 等方法后，不需要带上 Test 前缀
        String simpleClassName = removePrefix(table.getClassName(), upperFirst(table.getModuleName()));
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // 将 DictType 转换成 dict_type
        bindingMap.put("classNameVar", lowerFirst(simpleClassName)); // 将 DictType 转换成 dictType，用于变量
        bindingMap.put("moduleNameVar", lowerFirst(table.getModuleName()));
        bindingMap.put("bigModuleNameVar", upperFirst(table.getModuleName()));
        // 将 DictType 转换成 dict-type
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName, '-');
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        // permission 前缀
        bindingMap.put("permissionPrefix", table.getModuleName() + ":" + simpleClassNameStrikeCase);

        // 执行生成
        final Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(TEMPLATES.size()); // 有序
        TEMPLATES.forEach((vmPath, filePath) -> {
            filePath = formatFilePath(filePath, bindingMap);
            String content = templateEngine.getTemplate(vmPath).render(bindingMap);
            result.put(filePath, content);
        });
        return result;
    }

    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${basePackage}",
                getStr(bindingMap, "basePackage").replaceAll("\\.", "/"));
        filePath = StrUtil.replace(filePath, "${classNameVar}",
                getStr(bindingMap, "classNameVar"));

        filePath = StrUtil.replace(filePath, "${bigModuleNameVar}",
                getStr(bindingMap, "bigModuleNameVar"));
        // sceneEnum 包含的字段
        CodegenSceneEnum sceneEnum = (CodegenSceneEnum) bindingMap.get("sceneEnum");
        filePath = StrUtil.replace(filePath, "${sceneEnum.prefixClass}", sceneEnum.getPrefixClass());
        filePath = StrUtil.replace(filePath, "${sceneEnum.basePackage}", sceneEnum.getBasePackage());
        // table 包含的字段
        CodegenTableDO table = (CodegenTableDO) bindingMap.get("table");
        filePath = StrUtil.replace(filePath, "${table.moduleName}", table.getModuleName());
        filePath = StrUtil.replace(filePath, "${table.businessName}", table.getBusinessName());
        filePath = StrUtil.replace(filePath, "${table.className}", table.getClassName());
        return filePath;
    }

    private String formatTablesFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StrUtil.replace(filePath, "${moduleName}",
                getStr(bindingMap, "moduleName").replaceAll("\\.", "/"));
        return filePath;
    }

    private static String javaTemplatePath(String path) {
        return "codegen/ddd/java/" + path + ".vm";
    }

    private static String javaModuleImplVOFilePath(String path) {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "vo/${sceneEnum.prefixClass}${table.className}" + path, "biz", "main");
    }

    private static String javaModuleImplApplicationServiceFilePath() {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "${table.className}ApplicationService", "biz", "main");
    }

    private static String javaModuleImplControllerFilePath() {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "${sceneEnum.prefixClass}${table.className}Controller", "biz", "main");
    }

    private static String javaModuleImplMainFilePath(String path) {
        return javaModuleFilePath(path, "biz", "main");
    }

    private static String dddJavaModuleImplMainFilePath(String path) {
        return dddJavaModuleFilePath(path, "biz", "main");
    }

    private static String javaModuleApiMainFilePath(String path) {
        return javaModuleFilePath(path, "api", "main");
    }

    private static String javaModuleImplTestFilePath(String path) {
        return javaModuleFilePath(path, "biz", "test");
    }


    private static String dddJavaModuleFilePath(String path, String module, String src) {
        return //"bone-module-${table.moduleName}/" + // 顶级模块

                "bone-${table.moduleName}/" + // 子模块
                        "src/" + src + "/java/${basePackage}/${table.moduleName}/" + path + ".java";
    }

    private static String dddModuleFilePath(String filename) {
        return "bone-${table.moduleName}/" + filename;
    }


    private static String javaModuleFilePath(String path, String module, String src) {
        return "bone-module-${table.moduleName}/" + // 顶级模块
                "bone-module-${table.moduleName}-" + module + "/" + // 子模块
                "src/" + src + "/java/${basePackage}/module/${table.moduleName}/" + path + ".java";
    }

    private static String mapperXmlFilePath() {
        return "bone-module-${table.moduleName}/" + // 顶级模块
                "bone-module-${table.moduleName}-biz/" + // 子模块
                "src/main/resources/mapper/${table.businessName}/${table.className}Mapper.xml";
    }

    private static String reactTemplatePath(String path) {
        return "codegen/ddd/antdpro/" + path + ".vm";
    }

    private static String resourcesTemplatePath(String path) {
        return "codegen/ddd/resources/" + path + ".vm";
    }

    private static String resourcesFilePath(String moduleName,String path) {
        return "bone-"+moduleName+"/" + // 顶级目录
                "src/main/resources/" + path;
    }

    private static String reactFilePath(String moduleName,String path) {
        return "bone-ui-"+moduleName+"/" + // 顶级目录
                "src/" + path;
    }

    private static String vueTemplatePath(String path) {
        return "codegen/vue/" + path + ".vm";
    }

    private static String vueFilePath(String path) {
        return "bone-ui-${sceneEnum.basePackage}/" + // 顶级目录
                "src/" + path;
    }

    private static String vue3TemplatePath(String path) {
        return "codegen/vue3/" + path + ".vm";
    }

    private static String vue3FilePath(String path) {
        return "bone-ui-${sceneEnum.basePackage}-vue3/" + // 顶级目录
                "src/" + path;
    }
}
