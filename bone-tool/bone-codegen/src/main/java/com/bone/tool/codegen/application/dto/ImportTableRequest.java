package com.bone.tool.codegen.application.dto;

import lombok.Data;
import java.util.List;

/**
 * 导入表请求DTO
 * 用于接收从数据库导入表结构的请求参数
 *
 * @author bone-team
 */
@Data
public class ImportTableRequest {
    
    /**
     * 数据源配置ID
     */
    private Long dataSourceConfigId;
    
    /**
     * 要导入的表名列表
     */
    private List<String> tableNames;
    
    /**
     * 模块名称
     * 如：system、infra、tool等
     */
    private String moduleName;
    
    /**
     * 包路径
     * 如：com.bone.system
     */
    private String packageName;
    
    /**
     * 生成场景
     * 1: 单表 2: 主从表 3: 树表
     */
    private Integer scene;
    
    /**
     * 模板类型
     * 1: SaaS模式 2: DDD领域模型
     */
    private Integer modelType;
    
    public Long getDataSourceConfigId() {
        return dataSourceConfigId;
    }
    
    public void setDataSourceConfigId(Long dataSourceConfigId) {
        this.dataSourceConfigId = dataSourceConfigId;
    }
    
    public List<String> getTableNames() {
        return tableNames;
    }
    
    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
    }
    
    public String getModuleName() {
        return moduleName;
    }
    
    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public Integer getScene() {
        return scene;
    }
    
    public void setScene(Integer scene) {
        this.scene = scene;
    }
    
    public Integer getModelType() {
        return modelType;
    }
    
    public void setModelType(Integer modelType) {
        this.modelType = modelType;
    }
}