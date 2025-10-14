package com.bone.tool.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数据源配置 领域实体
 * <p>
 * 表示系统中配置的数据库连接信息，用于代码生成等操作
 *
 * @author bone-team
 */
@Table("infra_data_source_config")
@Data
@Accessors(chain = true)
public class DataSourceConfig extends AbstractEntity<Long> {

    /**
     * 主键编号常量 - 主数据源标识
     * <p>
     * 用于标识系统内置的主数据源
     */
    public static final Long ID_MASTER = 0L;

    /**
     * 数据源配置ID
     * <p>
     * 唯一标识一个数据源配置
     */
    @Id
    private Long id;
    /**
     * 数据源名称
     * <p>
     * 用于显示和标识不同的数据源配置
     */
    private String name;

    /**
     * 数据库连接URL
     * <p>
     * 包含数据库类型、主机、端口、数据库名等连接信息
     */
    private String url;
    /**
     * 数据库用户名
     */
    private String username;
    /**
     * 数据库密码
     * <p>
     * 存储时通常需要加密处理
     */
    private String password;
    
    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        this.id = id;
    }

}
