package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 领域实体 基础类
 * <p>
 * 所有领域实体的抽象基类，提供通用的审计和标识属性
 *
 * @author 芋道源码
 * @param <T> 主键类型
 */
@Data
@JsonIgnoreProperties(value = "transMap") // 由于 Easy-Trans 会添加 transMap 属性，避免 Jackson 在 Spring Cache 反序列化报错
public abstract class BaseDO<T extends Serializable> implements Serializable {

    /**
     * 创建时间
     * <p>表示实体首次创建的时间戳
     */
    private LocalDateTime createTime;
    
    /**
     * 最后更新时间
     * <p>表示实体最近一次被修改的时间戳
     */
    private LocalDateTime updateTime;
    
    /**
     * 创建者ID
     * <p>关联系统用户ID，记录创建该实体的操作者
     */
    private String creator;
    
    /**
     * 更新者ID
     * <p>关联系统用户ID，记录最后修改该实体的操作者
     */
    private String updater;
    
    /**
     * 软删除标记
     * <p>true表示已删除，false表示正常
     */
    private Boolean deleted;
    
    /**
     * 判断实体是否为新建状态
     * <p>当主键ID为null时，视为新建状态
     * 
     * @return 是否为新建实体
     */
    public boolean isNew() {
        return getId() == null;
    }
    
    /**
     * 获取实体主键ID
     * <p>子类必须实现此方法
     * 
     * @return 实体主键ID
     */
    public T getId() {
        throw new UnsupportedOperationException("子类必须实现getId方法");
    }
    
    /**
     * 设置实体主键ID
     * <p>子类必须实现此方法
     * 
     * @param id 实体主键ID
     */
    public void setId(T id) {
        throw new UnsupportedOperationException("子类必须实现setId方法");
    }

}
