package com.bone.tool.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;

/**
 * 数据源配置
 * 用于管理代码生成工具连接的数据库配置信息
 */
@Data
@Accessors(chain = true)
public class Datasource extends AbstractEntity<Long> implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主数据源ID常量 */
    public static final Long MASTER_ID = 0L;

    /** 主键ID */
    @Id
    private Long id;
    
    /** 数据源名称 */
    private String name;

    /** 数据库连接URL */
    private String url;
    
    /** 数据库用户名 */
    private String username;
    
    /** 数据库密码 */
    private String password;
    
    /** 数据库驱动类名 */
    private String driverClassName;

    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
