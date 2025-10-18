package com.bone.metadata.sdk.metadata.client;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import java.util.List;

public class FieldsByNamesRequest {
    // 添加无参构造器
    public FieldsByNamesRequest() {
    }
    
    // 全参构造器
    public FieldsByNamesRequest(AllocationContext context, List<String> logicalNames) {
        this.context = context;
        this.logicalNames = logicalNames;
    }
    
    private AllocationContext context;
    private List<String> logicalNames;
    
    // 手动添加getter和setter方法
    public AllocationContext getContext() {
        return context;
    }
    
    public void setContext(AllocationContext context) {
        this.context = context;
    }
    
    public List<String> getLogicalNames() {
        return logicalNames;
    }
    
    public void setLogicalNames(List<String> logicalNames) {
        this.logicalNames = logicalNames;
    }
}
