package com.bone.tool.codegen.domain.enums;

import cn.hutool.core.map.MapUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

public enum ModelTypeEnum {
    SAAS(1, "saas"),
    DDD(2, "ddd");

    private Integer type;
    private String name;
    
    private ModelTypeEnum(Integer type, String name) {
        this.type = type;
        this.name = name;
    }
    
    public Integer getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }

    // 获取模板类型
    public static ModelTypeEnum valueOf(Integer type) {
        return Optional.of(firstMatch(modelTypeEnum -> modelTypeEnum.getType().equals(type), values()))
                .orElseThrow(() -> new RuntimeException("未找到对应的模板类型"));
    }

    // 初始化模板
    public Map<String, String> getJavaTemplates(String modelType) {
        if ("saas".equals(modelType)) {
            return MapUtil.<String, String>builder(new LinkedHashMap<>())
                   .put(javaTemplatePath("application/dto/dto"), javaModuleFilePath("application/dto/${sceneEnum.prefixClass}${table.className}DTO"))
                    .put(javaTemplatePath("application/dto/query/pageQuery"), javaModuleFilePath("application/dto/query/${sceneEnum.prefixClass}${table.className}PageQuery"))
                    .put(javaTemplatePath("application/dto/query/query"), javaModuleFilePath("application/dto/query/${sceneEnum.prefixClass}${table.className}Query"))
                    .put(javaTemplatePath("application/applicationService"), javaModuleFilePath("application/${sceneEnum.prefixClass}${table.className}ApplicationService"))
                    .put(javaTemplatePath("application/converter/converter"), javaModuleFilePath("application/converter/${sceneEnum.prefixClass}${table.className}Converter"))
                    .put(javaTemplatePath("adapter/web/controller"), javaModuleFilePath("adapter/web/${sceneEnum.prefixClass}${table.className}Controller"))
                    .put(javaTemplatePath("domain/entity"), javaModuleSdkFilePath("model/${table.className}"))
                    .put(javaTemplatePath("domain/repository"), javaModuleFilePath("domain/repository/${table.className}Repository"))
                    .put(javaTemplatePath("domain/service"), javaModuleFilePath("domain/service/${table.className}Service"))
                    .put(javaTemplatePath("application"), javaModuleFilePath("${modelNameVar}Application"))
                    .put(javaTemplatePath("infrastructure/repositoryImpl"), javaModuleFilePath("infrastructure/persistence/${table.className}RepositoryImpl"))
                    .put(javaTemplatePath("infrastructure/securityConfig"), javaModuleFilePath("infrastructure/security/config/SecurityConfig"))
                    .build();

        } else if ("ddd".equals(modelType)) {
            return MapUtil.<String, String>builder(new LinkedHashMap<>())
                    .put(javaTemplatePath("application/dto/dto"), javaModuleFilePath("application/dto/${sceneEnum.prefixClass}${table.className}DTO"))
                    .put(javaTemplatePath("application/dto/query/pageQuery"), javaModuleFilePath("application/dto/query/${sceneEnum.prefixClass}${table.className}PageQuery"))
                    .put(javaTemplatePath("application/dto/query/query"), javaModuleFilePath("application/dto/query/${sceneEnum.prefixClass}${table.className}Query"))
                    .put(javaTemplatePath("application/applicationService"), javaModuleFilePath("application/${sceneEnum.prefixClass}${table.className}ApplicationService"))
                    .put(javaTemplatePath("application/converter/converter"), javaModuleFilePath("application/converter/${sceneEnum.prefixClass}${table.className}Converter"))
                    .put(javaTemplatePath("adapter/web/controller"), javaModuleFilePath("adapter/web/${sceneEnum.prefixClass}${table.className}Controller"))
                    .put(javaTemplatePath("domain/entity"), javaModuleFilePath("domain/model/${table.className}"))
                    .put(javaTemplatePath("domain/repository"), javaModuleFilePath("domain/repository/${table.className}Repository"))
                    .put(javaTemplatePath("domain/service"), javaModuleFilePath("domain/service/${table.className}Service"))
                    .put(javaTemplatePath("application"), javaModuleFilePath("${modelNameVar}Application"))
                    .put(javaTemplatePath("infrastructure/repositoryImpl"), javaModuleFilePath("infrastructure/persistence/${table.className}RepositoryImpl"))
                    .put(javaTemplatePath("infrastructure/securityConfig"), javaModuleFilePath("infrastructure/security/config/SecurityConfig"))
                    .build();
        }
        return new LinkedHashMap<>();
    }

    // 初始化配置文件模板
    public Map<String, String> getConfigTemplates(String modelType) {
        if ("saas".equals(modelType)) {
            return MapUtil.<String, String>builder(new LinkedHashMap<>())
                    .put("codegen/properties/pom.vm", "${table.moduleName}/pom.xml")
                    .put("codegen/properties/pom-parent.vm", "pom.xml")
                    .put("codegen/properties/pom-sdk.vm", "${table.moduleName}-sdk/pom.xml")
                    .put("codegen/properties/pom-ext.vm", "${table.moduleName}-ext/pom.xml")
                    .put("codegen/properties/application.vm", "${table.moduleName}/src/main/resources/application.yml")
                    .put("codegen/properties/application-local.vm", "${table.moduleName}/src/main/resources/application-local.yml")
                    .put("codegen/properties/application-test.vm", "${table.moduleName}/src/main/resources/application-test.yml")
                    .put("codegen/properties/application-prod.vm", "${table.moduleName}/src/main/resources/application-prod.yml")
                    .build();
        } else if ("ddd".equals(modelType)) {
            return MapUtil.<String, String>builder(new LinkedHashMap<>())
                    .put("codegen/properties/pom.vm", "${table.moduleName}/pom.xml")
                    .put("codegen/properties/pom-parent.vm", "pom.xml")
                    .put("codegen/properties/application.vm", "${table.moduleName}/src/main/resources/application.yml")
                    .put("codegen/properties/application-local.vm", "${table.moduleName}/src/main/resources/application-local.yml")
                    .put("codegen/properties/application-test.vm", "${table.moduleName}/src/main/resources/application-test.yml")
                    .put("codegen/properties/application-prod.vm", "${table.moduleName}/src/main/resources/application-prod.yml")
                    .build();
        }
        return new LinkedHashMap<>();
    }

    // 动态生成 Java 模板路径
    private static String javaTemplatePath(String path) {
        return "codegen/java/" + path + ".vm";
    }

    // 构建 Java 模块文件路径
    private static String javaModuleFilePath(String path) {
        return "${table.moduleName}/" + // 顶级模块
                "src/main/java/${table.packgeName}/" + path + ".java";
    }

    // 构建 Java SDK 模块文件路径
    private static String javaModuleSdkFilePath(String path) {
        return "${table.moduleName}-sdk/" + // 顶级模块
                "src/main/java/${table.packgeName}/sdk/" + path + ".java";
    }
}
