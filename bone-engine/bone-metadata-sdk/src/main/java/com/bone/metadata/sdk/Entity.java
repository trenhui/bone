package com.bone.metadata.sdk;

/**
 * 实体接口，所有实体类都应实现此接口
 *
 * @param <ID> 实体主键类型
 */
public interface Entity<ID> {
    
    /**
     * 获取实体ID
     *
     * @return 实体ID
     */
    ID getId();
    
    /**
     * 设置实体ID
     *
     * @param id 实体ID
     */
    void setId(ID id);
}