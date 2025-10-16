package com.bone.tool.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数据源配置
 */
@Table("infra_data_source_config")
@Data
@Accessors(chain = true)
public class DataSourceConfig extends AbstractEntity<Long> {

    /**
     * 主键编号常量 - 主数据源标识
     */
    public static final Long ID_MASTER = 0L;

    /**
     * 主键ID
     */
    @Id
    private Long id;
    
    /**
     * 数据源名称
     */
    private String name;

    /**
     * 连接URL
     */
    private String url;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 驱动类名
     */
    private String driverClassName;

}
