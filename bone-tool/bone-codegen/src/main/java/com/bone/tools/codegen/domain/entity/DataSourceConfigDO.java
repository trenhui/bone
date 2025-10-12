package com.bone.tools.codegen.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数据源配置
 *
 * @author 芋道源码
 */
@Table("infra_data_source_config")
@Data
@Accessors(chain = true)
public class DataSourceConfigDO extends BaseDO<Long> {

    /**
     * 主键编号 - Master 数据源
     */
    public static final Long ID_MASTER = 0L;

    /**
     * 主键编号
     */
    @Id
    private Long id;
    /**
     * 连接名
     */
    private String name;

    /**
     * 数据源连接
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
    
    @Override
    public Long getId() {
        return id;
    }
    
    @Override
    public void setId(Long id) {
        this.id = id;
    }

}
