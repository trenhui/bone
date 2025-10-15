package com.bone.tool.codegen.application.dto;

import lombok.Data;
import java.util.List;

/**
 * 代码生成请求DTO
 * 用于接收批量生成代码的请求参数
 *
 * @author bone-team
 */
@Data
public class GenerateCodeRequest {
    
    /**
     * 要生成代码的表ID列表
     */
    private List<Long> tableIds;
    
    /**
     * 分组ID
     * 用于组织生成的代码结构
     */
    private String groupId;
    
    /**
     * 模板类型
     * 1: SaaS模式 2: DDD领域模型
     */
    private Integer modelType;
    
    public List<Long> getTableIds() {
        return tableIds;
    }
    
    public void setTableIds(List<Long> tableIds) {
        this.tableIds = tableIds;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public Integer getModelType() {
        return modelType;
    }
    
    public void setModelType(Integer modelType) {
        this.modelType = modelType;
    }
}