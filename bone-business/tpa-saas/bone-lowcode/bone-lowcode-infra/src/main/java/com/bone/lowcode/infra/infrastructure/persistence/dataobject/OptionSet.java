package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;


@TableName("option_set")
@Data
public class OptionSet implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 节点类型,1：根节点,2：中间节点,3：叶子节点
     */
    private Byte nodeType;

    /**
     * 父节点id
     */
    private Long parentId;

    /**
     * 选项集适用范围
     */
    private Byte useScope;

    /**
     * 选项集描述
     */
    private String setDesc;

    /**
     * 元素名称
     */
    private String name;

    /**
     * 元素标识
     */
    private String code;

    /**
     * 选项值额外属性名,数组格式
     */
    private String extraPropertyKey;

    /**
     * 选项值额外属性,json格式
     */
    private String extraProperty;

    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    private Byte status;

    /**
     * 是否删除，0：正常，1：已删除
     */
    private Byte deleted;



    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改人
     */
    private String updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;

    public List<String> getExtraPropertyKeyList() {
        if (StringUtils.hasText(extraPropertyKey)) {
            List<String> array = JSON.parseArray(extraPropertyKey, String.class);
            return array;
        }
        return List.of();
    }

    public Map<String, String> getExtraPropertyKeyValue() {
        if (StringUtils.hasText(extraProperty)) {
            return JSON.parseObject(
                    extraProperty,
                    new TypeReference<>() {
                    }
            );
        }
        return Map.of();
    }
}
