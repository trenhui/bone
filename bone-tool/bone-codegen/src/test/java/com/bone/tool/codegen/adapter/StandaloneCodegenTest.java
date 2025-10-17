package com.bone.tool.codegen.adapter;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 独立的代码生成测试类
 * 不依赖Spring上下文，直接生成代码文件并打包为ZIP
 */
public class StandaloneCodegenTest {

    /**
     * 测试生成用户表代码并打包为ZIP文件
     */
    @Test
    public void testGenerateUserTableCode() throws Exception {
        // 创建输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 定义输出ZIP文件路径
        String zipFilePath = outputDir.resolve("user-code.zip").toString();
        
        // 生成代码并打包为ZIP
        generateCodeZip(zipFilePath, "user");
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(zipFilePath)), "代码ZIP文件未生成");
        assertTrue(Files.size(Paths.get(zipFilePath)) > 0, "生成的ZIP文件为空");
        
        System.out.println("代码生成成功！ZIP文件路径: " + zipFilePath);
    }

    /**
     * 测试生成多个表的代码并打包为ZIP文件
     */
    @Test
    public void testGenerateMultiTableCode() throws Exception {
        // 创建输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 定义输出ZIP文件路径
        String zipFilePath = outputDir.resolve("multi-tables-code.zip").toString();
        
        // 生成多个表的代码并打包为ZIP
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            // 生成用户表代码
            generateTableCode(zos, "user");
            // 生成角色表代码
            generateTableCode(zos, "role");
            // 生成权限表代码
            generateTableCode(zos, "permission");
            
            zos.finish();
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(zipFilePath)), "多表代码ZIP文件未生成");
        assertTrue(Files.size(Paths.get(zipFilePath)) > 0, "生成的ZIP文件为空");
        
        System.out.println("多表代码生成成功！ZIP文件路径: " + zipFilePath);
    }
    
    /**
     * 测试生成Saas标准模板代码
     */
    @Test
    public void testGenerateSaasStandardCode() throws Exception {
        // 创建输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 定义输出ZIP文件路径
        String zipFilePath = outputDir.resolve("saas-standard-code.zip").toString();
        
        // 生成Saas标准模板代码并打包为ZIP
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            // 使用Saas标准模板生成用户表代码
            generateTableCode(zos, "user", "saas-standard");
            
            zos.finish();
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(zipFilePath)), "Saas标准模板代码ZIP文件未生成");
        assertTrue(Files.size(Paths.get(zipFilePath)) > 0, "生成的ZIP文件为空");
        
        System.out.println("Saas标准模板代码生成成功！ZIP文件路径: " + zipFilePath);
    }
    
    /**
     * 测试生成Saas扩展模板代码
     */
    @Test
    public void testGenerateSaasExtendedCode() throws Exception {
        // 创建输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 定义输出ZIP文件路径
        String zipFilePath = outputDir.resolve("saas-extended-code.zip").toString();
        
        // 生成Saas扩展模板代码并打包为ZIP
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            // 使用Saas扩展模板生成用户表代码
            generateTableCode(zos, "user", "saas-extended");
            
            // 生成租户相关代码
            generateTenantCode(zos);
            
            zos.finish();
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(zipFilePath)), "Saas扩展模板代码ZIP文件未生成");
        assertTrue(Files.size(Paths.get(zipFilePath)) > 0, "生成的ZIP文件为空");
        
        System.out.println("Saas扩展模板代码生成成功！ZIP文件路径: " + zipFilePath);
    }

    /**
     * 为单个表生成代码并打包为ZIP文件
     */
    private void generateCodeZip(String zipFilePath, String tableName) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            generateTableCode(zos, tableName);
            zos.finish();
        }
    }
    
    /**
     * 为单个表生成代码并打包为ZIP文件（支持模板类型）
     */
    private void generateCodeZip(String zipFilePath, String tableName, String templateType) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            generateTableCode(zos, tableName, templateType);
            
            // 如果是Saas扩展模板，生成租户相关代码
            if ("saas-extended".equals(templateType)) {
                generateTenantCode(zos);
            }
            
            zos.finish();
        }
    }

    /**
     * 为指定表生成各种代码文件并添加到ZIP输出流
     */
    private void generateTableCode(ZipOutputStream zos, String tableName) throws IOException {
        generateTableCode(zos, tableName, "standard");
    }
    
    /**
     * 为指定表生成各种代码文件并添加到ZIP输出流（支持模板类型）
     */
    private void generateTableCode(ZipOutputStream zos, String tableName, String templateType) throws IOException {
        // 转换表名为类名（首字母大写驼峰）
        String className = convertToCamelCase(tableName, true);
        
        // 生成实体类（根据模板类型选择不同的生成方法）
        String entityCode;
        String mapperCode;
        String serviceCode;
        String serviceImplCode;
        String controllerCode;
        
        if ("saas-standard".equals(templateType) || "saas-extended".equals(templateType)) {
            // Saas模板，包含租户字段
            entityCode = generateSaasEntityClass(className, tableName);
            mapperCode = generateSaasMapperClass(className);
            serviceCode = generateSaasServiceInterface(className);
            serviceImplCode = generateSaasServiceImplClass(className);
            controllerCode = generateSaasControllerClass(className);
        } else {
            // 标准模板
            entityCode = generateEntityClass(className, tableName);
            mapperCode = generateMapperClass(className);
            serviceCode = generateServiceInterface(className);
            serviceImplCode = generateServiceImplClass(className);
            controllerCode = generateControllerClass(className);
        }
        
        // 添加到ZIP文件
        addToZip(zos, "src/main/java/com/example/entity/" + className + ".java", entityCode);
        addToZip(zos, "src/main/java/com/example/mapper/" + className + "Mapper.java", mapperCode);
        addToZip(zos, "src/main/java/com/example/service/" + className + "Service.java", serviceCode);
        addToZip(zos, "src/main/java/com/example/service/impl/" + className + "ServiceImpl.java", serviceImplCode);
        addToZip(zos, "src/main/java/com/example/controller/" + className + "Controller.java", controllerCode);
    }
    
    /**
     * 生成租户相关代码
     */
    private void generateTenantCode(ZipOutputStream zos) throws IOException {
        // 生成租户实体类
        String tenantEntityCode = generateTenantEntityClass();
        addToZip(zos, "src/main/java/com/example/entity/Tenant.java", tenantEntityCode);
        
        // 生成租户Mapper
        String tenantMapperCode = generateTenantMapperClass();
        addToZip(zos, "src/main/java/com/example/mapper/TenantMapper.java", tenantMapperCode);
        
        // 生成租户Service
        String tenantServiceCode = generateTenantServiceInterface();
        addToZip(zos, "src/main/java/com/example/service/TenantService.java", tenantServiceCode);
        
        // 生成租户Service实现
        String tenantServiceImplCode = generateTenantServiceImplClass();
        addToZip(zos, "src/main/java/com/example/service/impl/TenantServiceImpl.java", tenantServiceImplCode);
        
        // 生成租户Controller
        String tenantControllerCode = generateTenantControllerClass();
        addToZip(zos, "src/main/java/com/example/controller/TenantController.java", tenantControllerCode);
        
        // 生成租户过滤器
        String tenantFilterCode = generateTenantFilterClass();
        addToZip(zos, "src/main/java/com/example/filter/TenantFilter.java", tenantFilterCode);
    }

    /**
     * 将生成的代码文件添加到ZIP输出流
     */
    private void addToZip(ZipOutputStream zos, String filePath, String content) throws IOException {
        ZipEntry entry = new ZipEntry(filePath);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * 生成实体类代码
     */
    private String generateEntityClass(String className, String tableName) {
        return "package com.example.entity;\n\n" +
               "import java.util.Date;\n" +
               "/**\n" +
               " * " + tableName + "实体类\n" +
               " */\n" +
               "public class " + className + " {\n" +
               "    private Long id;\n" +
               "    private String name;\n" +
               "    private String code;\n" +
               "    private Integer status;\n" +
               "    private Date createTime;\n" +
               "    private Date updateTime;\n" +
               "\n" +
               "    // Getters and Setters\n" +
               "    public Long getId() { return id; }\n" +
               "    public void setId(Long id) { this.id = id; }\n" +
               "    public String getName() { return name; }\n" +
               "    public void setName(String name) { this.name = name; }\n" +
               "    public String getCode() { return code; }\n" +
               "    public void setCode(String code) { this.code = code; }\n" +
               "    public Integer getStatus() { return status; }\n" +
               "    public void setStatus(Integer status) { this.status = status; }\n" +
               "    public Date getCreateTime() { return createTime; }\n" +
               "    public void setCreateTime(Date createTime) { this.createTime = createTime; }\n" +
               "    public Date getUpdateTime() { return updateTime; }\n" +
               "    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }\n" +
               "}\n";
    }
    
    /**
     * 生成Saas实体类代码（包含租户字段）
     */
    private String generateSaasEntityClass(String className, String tableName) {
        return "package com.example.entity;\n\n" +
               "import java.util.Date;\n" +
               "/**\n" +
               " * " + tableName + "实体类（Saas版本）\n" +
               " */\n" +
               "public class " + className + " {\n" +
               "    private Long id;\n" +
               "    private Long tenantId; // 租户ID\n" +
               "    private String name;\n" +
               "    private String code;\n" +
               "    private Integer status;\n" +
               "    private Date createTime;\n" +
               "    private Date updateTime;\n" +
               "\n" +
               "    // Getters and Setters\n" +
               "    public Long getId() { return id; }\n" +
               "    public void setId(Long id) { this.id = id; }\n" +
               "    public Long getTenantId() { return tenantId; }\n" +
               "    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }\n" +
               "    public String getName() { return name; }\n" +
               "    public void setName(String name) { this.name = name; }\n" +
               "    public String getCode() { return code; }\n" +
               "    public void setCode(String code) { this.code = code; }\n" +
               "    public Integer getStatus() { return status; }\n" +
               "    public void setStatus(Integer status) { this.status = status; }\n" +
               "    public Date getCreateTime() { return createTime; }\n" +
               "    public void setCreateTime(Date createTime) { this.createTime = createTime; }\n" +
               "    public Date getUpdateTime() { return updateTime; }\n" +
               "    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }\n" +
               "}\n";
    }
    }

    /**
     * 生成Mapper接口代码
     */
    private String generateMapperClass(String className) {
        return "package com.example.mapper;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Mapper接口\n" +
               " */\n" +
               "public interface " + className + "Mapper {\n" +
               "    " + className + " selectById(Long id);\n" +
               "    List<" + className + "> selectList();\n" +
               "    int insert(" + className + " entity);\n" +
               "    int update(" + className + " entity);\n" +
               "    int deleteById(Long id);\n" +
               "}\n";
    }
    
    /**
     * 生成Saas Mapper接口代码
     */
    private String generateSaasMapperClass(String className) {
        return "package com.example.mapper;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Mapper接口（Saas版本）\n" +
               " */\n" +
               "public interface " + className + "Mapper {\n" +
               "    " + className + " selectById(Long id);\n" +
               "    " + className + " selectByTenantIdAndId(Long tenantId, Long id);\n" +
               "    List<" + className + "> selectList();\n" +
               "    List<" + className + "> selectListByTenantId(Long tenantId);\n" +
               "    int insert(" + className + " entity);\n" +
               "    int update(" + className + " entity);\n" +
               "    int deleteById(Long id);\n" +
               "    int deleteByTenantId(Long tenantId);\n" +
               "}\n";
    }
    }

    /**
     * 生成Service接口代码
     */
    private String generateServiceInterface(String className) {
        return "package com.example.service;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Service接口\n" +
               " */\n" +
               "public interface " + className + "Service {\n" +
               "    " + className + " getById(Long id);\n" +
               "    List<" + className + "> list();\n" +
               "    boolean save(" + className + " entity);\n" +
               "    boolean update(" + className + " entity);\n" +
               "    boolean remove(Long id);\n" +
               "}\n";
    }\n    
    /**
     * 生成Saas Service接口代码
     */
    private String generateSaasServiceInterface(String className) {
        return "package com.example.service;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Service接口（Saas版本）\n" +
               " */\n" +
               "public interface " + className + "Service {\n" +
               "    " + className + " getById(Long id);\n" +
               "    " + className + " getByTenantIdAndId(Long tenantId, Long id);\n" +
               "    List<" + className + "> list();\n" +
               "    List<" + className + "> listByTenantId(Long tenantId);\n" +
               "    boolean save(" + className + " entity);\n" +
               "    boolean update(" + className + " entity);\n" +
               "    boolean remove(Long id);\n" +
               "    boolean removeByTenantId(Long tenantId);\n" +
               "}\n";
    }
    }

    /**
     * 生成Service实现类代码
     */
    private String generateServiceImplClass(String className) {
        String uncapitalizedName = className.substring(0, 1).toLowerCase() + className.substring(1);
        return "package com.example.service.impl;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import com.example.mapper." + className + "Mapper;\n" +
               "import com.example.service." + className + "Service;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Service实现类\n" +
               " */\n" +
               "@Service\n" +
               "public class " + className + "ServiceImpl implements " + className + "Service {\n" +
               "\n" +
               "    @Resource\n" +
               "    private " + className + "Mapper " + uncapitalizedName + "Mapper;\n" +
               "\n" +
               "    @Override\n" +
               "    public " + className + " getById(Long id) {\n" +
               "        return " + uncapitalizedName + "Mapper.selectById(id);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public List<" + className + "> list() {\n" +
               "        return " + uncapitalizedName + "Mapper.selectList();\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean save(" + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Mapper.insert(entity) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean update(" + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Mapper.update(entity) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean remove(Long id) {\n" +
               "        return " + uncapitalizedName + "Mapper.deleteById(id) > 0;\n" +
               "    }\n" +
               "}\n";
    }\n    
    /**
     * 生成Saas Service实现类代码
     */
    private String generateSaasServiceImplClass(String className) {
        String uncapitalizedName = className.substring(0, 1).toLowerCase() + className.substring(1);
        return "package com.example.service.impl;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import com.example.mapper." + className + "Mapper;\n" +
               "import com.example.service." + className + "Service;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Service实现类（Saas版本）\n" +
               " */\n" +
               "@Service\n" +
               "public class " + className + "ServiceImpl implements " + className + "Service {\n" +
               "\n" +
               "    @Resource\n" +
               "    private " + className + "Mapper " + uncapitalizedName + "Mapper;\n" +
               "\n" +
               "    @Override\n" +
               "    public " + className + " getById(Long id) {\n" +
               "        return " + uncapitalizedName + "Mapper.selectById(id);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public " + className + " getByTenantIdAndId(Long tenantId, Long id) {\n" +
               "        return " + uncapitalizedName + "Mapper.selectByTenantIdAndId(tenantId, id);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public List<" + className + "> list() {\n" +
               "        return " + uncapitalizedName + "Mapper.selectList();\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public List<" + className + "> listByTenantId(Long tenantId) {\n" +
               "        return " + uncapitalizedName + "Mapper.selectListByTenantId(tenantId);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean save(" + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Mapper.insert(entity) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean update(" + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Mapper.update(entity) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean remove(Long id) {\n" +
               "        return " + uncapitalizedName + "Mapper.deleteById(id) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean removeByTenantId(Long tenantId) {\n" +
               "        return " + uncapitalizedName + "Mapper.deleteByTenantId(tenantId) > 0;\n" +
               "    }\n" +
               "}\n";
    }
    }

    /**
     * 生成Controller代码
     */
    private String generateControllerClass(String className) {
        String uncapitalizedName = className.substring(0, 1).toLowerCase() + className.substring(1);
        String path = uncapitalizedName.replaceAll("([A-Z])", "-$1").toLowerCase();
        return "package com.example.controller;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import com.example.service." + className + "Service;\n" +
               "import org.springframework.web.bind.annotation.*;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Controller\n" +
               " */\n" +
               "@RestController\n" +
               "@RequestMapping(\"/api/" + path + ")\n" +
               "public class " + className + "Controller {\n" +
               "\n" +
               "    @Resource\n" +
               "    private " + className + "Service " + uncapitalizedName + "Service;\n" +
               "\n" +
               "    @GetMapping(\"/{id}\")\n" +
               "    public " + className + " getById(@PathVariable Long id) {\n" +
               "        return " + uncapitalizedName + "Service.getById(id);\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping\n" +
               "    public List<" + className + "> list() {\n" +
               "        return " + uncapitalizedName + "Service.list();\n" +
               "    }\n" +
               "\n" +
               "    @PostMapping\n" +
               "    public boolean save(@RequestBody " + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Service.save(entity);\n" +
               "    }\n" +
               "\n" +
               "    @PutMapping\n" +
               "    public boolean update(@RequestBody " + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Service.update(entity);\n" +
               "    }\n" +
               "\n" +
               "    @DeleteMapping(\"/{id}\")\n" +
               "    public boolean remove(@PathVariable Long id) {\n" +
               "        return " + uncapitalizedName + "Service.remove(id);\n" +
               "    }\n" +
               "}\n";
    }\n    
    /**
     * 生成Saas Controller代码
     */
    private String generateSaasControllerClass(String className) {
        String uncapitalizedName = className.substring(0, 1).toLowerCase() + className.substring(1);
        String path = uncapitalizedName.replaceAll("([A-Z])", "-$1").toLowerCase();
        return "package com.example.controller;\n\n" +
               "import com.example.entity." + className + ";\n" +
               "import com.example.service." + className + "Service;\n" +
               "import org.springframework.web.bind.annotation.*;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * " + className + "Controller（Saas版本）\n" +
               " */\n" +
               "@RestController\n" +
               "@RequestMapping(\"/api/" + path + ")\n" +
               "public class " + className + "Controller {\n" +
               "\n" +
               "    @Resource\n" +
               "    private " + className + "Service " + uncapitalizedName + "Service;\n" +
               "\n" +
               "    @GetMapping(\"/{id}\")\n" +
               "    public " + className + " getById(@PathVariable Long id) {\n" +
               "        return " + uncapitalizedName + "Service.getById(id);\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping(\"/tenant/{tenantId}/{id}\")\n" +
               "    public " + className + " getByTenantIdAndId(@PathVariable Long tenantId, @PathVariable Long id) {\n" +
               "        return " + uncapitalizedName + "Service.getByTenantIdAndId(tenantId, id);\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping\n" +
               "    public List<" + className + "> list() {\n" +
               "        return " + uncapitalizedName + "Service.list();\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping(\"/tenant/{tenantId}\")\n" +
               "    public List<" + className + "> listByTenantId(@PathVariable Long tenantId) {\n" +
               "        return " + uncapitalizedName + "Service.listByTenantId(tenantId);\n" +
               "    }\n" +
               "\n" +
               "    @PostMapping\n" +
               "    public boolean save(@RequestBody " + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Service.save(entity);\n" +
               "    }\n" +
               "\n" +
               "    @PutMapping\n" +
               "    public boolean update(@RequestBody " + className + " entity) {\n" +
               "        return " + uncapitalizedName + "Service.update(entity);\n" +
               "    }\n" +
               "\n" +
               "    @DeleteMapping(\"/{id}\")\n" +
               "    public boolean remove(@PathVariable Long id) {\n" +
               "        return " + uncapitalizedName + "Service.remove(id);\n" +
               "    }\n" +
               "\n" +
               "    @DeleteMapping(\"/tenant/{tenantId}\")\n" +
               "    public boolean removeByTenantId(@PathVariable Long tenantId) {\n" +
               "        return " + uncapitalizedName + "Service.removeByTenantId(tenantId);\n" +
               "    }\n" +
               "}\n";
    }
    }

    /**
     * 转换字符串为驼峰命名
     */
    private String convertToCamelCase(String str, boolean capitalizeFirst) {
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = capitalizeFirst;
        
        for (char c : str.toCharArray()) {
            if (c == '_' || c == '-') {
                nextUpperCase = true;
            } else {
                result.append(nextUpperCase ? Character.toUpperCase(c) : Character.toLowerCase(c));
                nextUpperCase = false;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 生成租户实体类
     */
    private String generateTenantEntityClass() {
        return "package com.example.entity;\n\n" +
               "import java.util.Date;\n" +
               "/**\n" +
               " * 租户实体类\n" +
               " */\n" +
               "public class Tenant {\n" +
               "    private Long id;\n" +
               "    private String tenantCode; // 租户编码\n" +
               "    private String tenantName; // 租户名称\n" +
               "    private String contactPerson; // 联系人\n" +
               "    private String contactPhone; // 联系电话\n" +
               "    private String email; // 邮箱\n" +
               "    private Integer status; // 状态：0-禁用，1-启用\n" +
               "    private Date expireTime; // 过期时间\n" +
               "    private Date createTime;\n" +
               "    private Date updateTime;\n" +
               "\n" +
               "    // Getters and Setters\n" +
               "    public Long getId() { return id; }\n" +
               "    public void setId(Long id) { this.id = id; }\n" +
               "    public String getTenantCode() { return tenantCode; }\n" +
               "    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }\n" +
               "    public String getTenantName() { return tenantName; }\n" +
               "    public void setTenantName(String tenantName) { this.tenantName = tenantName; }\n" +
               "    public String getContactPerson() { return contactPerson; }\n" +
               "    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }\n" +
               "    public String getContactPhone() { return contactPhone; }\n" +
               "    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }\n" +
               "    public String getEmail() { return email; }\n" +
               "    public void setEmail(String email) { this.email = email; }\n" +
               "    public Integer getStatus() { return status; }\n" +
               "    public void setStatus(Integer status) { this.status = status; }\n" +
               "    public Date getExpireTime() { return expireTime; }\n" +
               "    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }\n" +
               "    public Date getCreateTime() { return createTime; }\n" +
               "    public void setCreateTime(Date createTime) { this.createTime = createTime; }\n" +
               "    public Date getUpdateTime() { return updateTime; }\n" +
               "    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }\n" +
               "}\n";
    }
    
    /**
     * 生成租户Mapper接口
     */
    private String generateTenantMapperClass() {
        return "package com.example.mapper;\n\n" +
               "import com.example.entity.Tenant;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * 租户Mapper接口\n" +
               " */\n" +
               "public interface TenantMapper {\n" +
               "    Tenant selectById(Long id);\n" +
               "    Tenant selectByTenantCode(String tenantCode);\n" +
               "    List<Tenant> selectList();\n" +
               "    List<Tenant> selectListByStatus(Integer status);\n" +
               "    int insert(Tenant tenant);\n" +
               "    int update(Tenant tenant);\n" +
               "    int updateStatus(Long id, Integer status);\n" +
               "    int deleteById(Long id);\n" +
               "}\n";
    }
    
    /**
     * 生成租户Service接口
     */
    private String generateTenantServiceInterface() {
        return "package com.example.service;\n\n" +
               "import com.example.entity.Tenant;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * 租户Service接口\n" +
               " */\n" +
               "public interface TenantService {\n" +
               "    Tenant getById(Long id);\n" +
               "    Tenant getByTenantCode(String tenantCode);\n" +
               "    List<Tenant> list();\n" +
               "    List<Tenant> listByStatus(Integer status);\n" +
               "    boolean save(Tenant tenant);\n" +
               "    boolean update(Tenant tenant);\n" +
               "    boolean updateStatus(Long id, Integer status);\n" +
               "    boolean remove(Long id);\n" +
               "}\n";
    }
    
    /**
     * 生成租户Service实现类
     */
    private String generateTenantServiceImplClass() {
        return "package com.example.service.impl;\n\n" +
               "import com.example.entity.Tenant;\n" +
               "import com.example.mapper.TenantMapper;\n" +
               "import com.example.service.TenantService;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * 租户Service实现类\n" +
               " */\n" +
               "@Service\n" +
               "public class TenantServiceImpl implements TenantService {\n" +
               "\n" +
               "    @Resource\n" +
               "    private TenantMapper tenantMapper;\n" +
               "\n" +
               "    @Override\n" +
               "    public Tenant getById(Long id) {\n" +
               "        return tenantMapper.selectById(id);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public Tenant getByTenantCode(String tenantCode) {\n" +
               "        return tenantMapper.selectByTenantCode(tenantCode);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public List<Tenant> list() {\n" +
               "        return tenantMapper.selectList();\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public List<Tenant> listByStatus(Integer status) {\n" +
               "        return tenantMapper.selectListByStatus(status);\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean save(Tenant tenant) {\n" +
               "        return tenantMapper.insert(tenant) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean update(Tenant tenant) {\n" +
               "        return tenantMapper.update(tenant) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean updateStatus(Long id, Integer status) {\n" +
               "        return tenantMapper.updateStatus(id, status) > 0;\n" +
               "    }\n" +
               "\n" +
               "    @Override\n" +
               "    public boolean remove(Long id) {\n" +
               "        return tenantMapper.deleteById(id) > 0;\n" +
               "    }\n" +
               "}\n";
    }
    
    /**
     * 生成租户Controller
     */
    private String generateTenantControllerClass() {
        return "package com.example.controller;\n\n" +
               "import com.example.entity.Tenant;\n" +
               "import com.example.service.TenantService;\n" +
               "import org.springframework.web.bind.annotation.*;\n" +
               "import javax.annotation.Resource;\n" +
               "import java.util.List;\n" +
               "/**\n" +
               " * 租户Controller\n" +
               " */\n" +
               "@RestController\n" +
               "@RequestMapping(\"/api/tenant\")\n" +
               "public class TenantController {\n" +
               "\n" +
               "    @Resource\n" +
               "    private TenantService tenantService;\n" +
               "\n" +
               "    @GetMapping(\"/{id}\")\n" +
               "    public Tenant getById(@PathVariable Long id) {\n" +
               "        return tenantService.getById(id);\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping(\"/code/{tenantCode}\")\n" +
               "    public Tenant getByTenantCode(@PathVariable String tenantCode) {\n" +
               "        return tenantService.getByTenantCode(tenantCode);\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping\n" +
               "    public List<Tenant> list() {\n" +
               "        return tenantService.list();\n" +
               "    }\n" +
               "\n" +
               "    @GetMapping(\"/status/{status}\")\n" +
               "    public List<Tenant> listByStatus(@PathVariable Integer status) {\n" +
               "        return tenantService.listByStatus(status);\n" +
               "    }\n" +
               "\n" +
               "    @PostMapping\n" +
               "    public boolean save(@RequestBody Tenant tenant) {\n" +
               "        return tenantService.save(tenant);\n" +
               "    }\n" +
               "\n" +
               "    @PutMapping\n" +
               "    public boolean update(@RequestBody Tenant tenant) {\n" +
               "        return tenantService.update(tenant);\n" +
               "    }\n" +
               "\n" +
               "    @PutMapping(\"/{id}/status/{status}\")\n" +
               "    public boolean updateStatus(@PathVariable Long id, @PathVariable Integer status) {\n" +
               "        return tenantService.updateStatus(id, status);\n" +
               "    }\n" +
               "\n" +
               "    @DeleteMapping(\"/{id}\")\n" +
               "    public boolean remove(@PathVariable Long id) {\n" +
               "        return tenantService.remove(id);\n" +
               "    }\n" +
               "}\n";
    }
    
    /**
     * 生成租户过滤器
     */
    private String generateTenantFilterClass() {
        return "package com.example.filter;\n\n" +
               "import org.springframework.stereotype.Component;\n" +
               "import javax.servlet.*;\n" +
               "import javax.servlet.http.HttpServletRequest;\n" +
               "import java.io.IOException;\n" +
               "/**\n" +
               " * 租户过滤器\n" +
               " * 用于处理租户上下文信息\n" +
               " */\n" +
               "@Component\n" +
               "public class TenantFilter implements Filter {\n" +
               "\n" +
               "    @Override\n" +
               "    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) \n" +
               "            throws IOException, ServletException {\n" +
               "        HttpServletRequest httpRequest = (HttpServletRequest) request;\n" +
               "        \n" +
               "        try {\n" +
               "            // 从请求头或参数中获取租户ID\n" +
               "            String tenantId = httpRequest.getHeader(\"X-Tenant-Id\");\n" +
               "            if (tenantId == null || tenantId.isEmpty()) {\n" +
               "                tenantId = httpRequest.getParameter(\"tenantId\");\n" +
               "            }\n" +
               "            \n" +
               "            // 设置租户上下文\n" +
               "            if (tenantId != null && !tenantId.isEmpty()) {\n" +
               "                TenantContextHolder.setTenantId(Long.parseLong(tenantId));\n" +
               "            }\n" +
               "            \n" +
               "            chain.doFilter(request, response);\n" +
               "        } finally {\n" +
               "            // 清除租户上下文\n" +
               "            TenantContextHolder.clear();\n" +
               "        }\n" +
               "    }\n" +
               "}\n";
    }
}