package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("meta_biz_model")
public class MetaBizModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    //对应数据表名称
    private String tableName;

    /**
     * 编码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 专属字段前缀
     */
    private String extraFieldPrefix;

    /**
     * 取值表达式前缀
     */
    private String dataBindingPrefix;

    /**
     * 字段code
     */
    private String fieldCode;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 组件类型
     */
    private String componentType;

    /**
     * 是否启用,0:不启用，1:启用
     */
    private Byte status;

    /**
     * 是否删除，0：正常，1：已删除
     */
    private Byte deleted;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改人
     */
    private Long updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;
}
