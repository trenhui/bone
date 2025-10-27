package com.bone.metadata.sdk.test.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 简化的外部脚本执行器
 * 移除了所有外部依赖
 */
public class ExternalScriptExecutor {
    
    /**
     * 数据库类型
     */
    private String dbType;
    
    /**
     * 配置映射
     */
    private final Map<String, String> configs = new HashMap<>();
    
    /**
     * 简化的构造器
     */
    public ExternalScriptExecutor() {
        this.dbType = "h2"; // 默认使用H2数据库
        initDefaultConfigs();
    }
    
    /**
     * 使用指定数据库类型的构造器
     */
    public ExternalScriptExecutor(String dbType) {
        this.dbType = dbType;
        initDefaultConfigs();
    }
    
    /**
     * 初始化默认配置
     */
    private void initDefaultConfigs() {
        configs.put("spring.datasource.url", "jdbc:h2:mem:test");
        configs.put("spring.datasource.username", "sa");
        configs.put("spring.datasource.password", "");
    }
    
    /**
     * 执行脚本（简化版本）
     */
    public void executeScripts() {
        System.out.println("开始执行SQL脚本...");
        
        if (dbType == null || dbType.isEmpty()) {
            dbType = detectDbTypeFromUrl(configs.get("spring.datasource.url"));
        }
        
        runScripts(dbType);
        
        System.out.println("SQL脚本执行完成");
    }
    
    /**
     * 从URL检测数据库类型
     */
    private String detectDbTypeFromUrl(String url) {
        if (url == null) {
            return "h2";
        }
        
        url = url.toLowerCase();
        if (url.contains("mysql:")) {
            return "mysql";
        } else if (url.contains("oracle:")) {
            return "oracle";
        } else if (url.contains("postgresql:")) {
            return "postgresql";
        } else if (url.contains("sqlserver:")) {
            return "sqlserver";
        } else if (url.contains("h2:")) {
            return "h2";
        } else {
            System.out.println("未识别的数据源类型，默认使用 H2");
            return "h2";
        }
    }
    
    /**
     * 运行脚本（简化版本）
     */
    private void runScripts(String dbType) {
        String[] scriptPaths = {
                String.format("sql/%s/schema.sql", dbType),
                String.format("sql/%s/ext_schema.sql", dbType),
                String.format("sql/%s/data.sql", dbType)
        };
        
        for (String scriptPath : scriptPaths) {
            // 模拟脚本执行
            simulateScriptExecution(scriptPath);
        }
    }
    
    /**
     * 模拟脚本执行
     */
    private void simulateScriptExecution(String scriptPath) {
        System.out.println("模拟执行脚本: " + scriptPath);
        // 这里只是简单的模拟，实际不执行任何SQL
        // 在实际测试中，这里可能会读取脚本文件并执行SQL语句
    }
    
    /**
     * 设置配置
     */
    public void setConfig(String key, String value) {
        configs.put(key, value);
    }
    
    /**
     * 获取配置
     */
    public String getConfig(String key) {
        return configs.get(key);
    }
    
    /**
     * 设置数据库类型
     */
    public void setDbType(String dbType) {
        this.dbType = dbType;
    }
    
    /**
     * 获取数据库类型
     */
    public String getDbType() {
        return dbType;
    }
}