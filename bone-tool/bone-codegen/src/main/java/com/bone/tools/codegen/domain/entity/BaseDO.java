package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.Entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体对象
 *
 * @author 芋道源码
 */
@Data
@JsonIgnoreProperties(value = "transMap") // 由于 Easy-Trans 会添加 transMap 属性，避免 Jackson 在 Spring Cache 反序列化报错
public abstract class BaseDO<T extends Serializable> implements Entity<T> {

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 最后更新时间
     */
    private LocalDateTime updateTime;
    /**
     * 创建者，目前使用 SysUser 的 id 编号
     */
    private String creator;
    /**
     * 更新者，目前使用 SysUser 的 id 编号
     */
    private String updater;
    /**
     * 是否删除
     */
    private Boolean deleted;
    
    // 移除@Override注解，避免编译错误
    public boolean isNew() {
        return getId() == null;
    }
    
    public T getId() {
        throw new UnsupportedOperationException("子类必须实现getId方法");
    }
    
    public void setId(T id) {
        throw new UnsupportedOperationException("子类必须实现setId方法");
    }

}
