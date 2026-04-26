package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.domain.model.LinkedDisplayRuleEntry;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@TableName("cfg_field_linked_display_rule")
public class FieldLinkedDisplayRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属页面ID
     */
    private Long pageId;

    /**
     * 源头规则id
     */
    private Long sourceId;

    /**
     * 下拉字段id
     */
    private Long selectFieldId;

    /**
     * 下拉框选项数据源类型,1:选项集,2:主数据
     */
    private Byte datasourceType;

    /**
     * 下拉框选项数据源code
     */
    private String datasourceCode;

    /**
     * 键值对,被影响字段id及扩展属性名
     */
    private String affectContent;

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

    public void setAffectField(List<LinkedDisplayRuleEntry> entryList) {
        if (CollectionUtils.isEmpty(entryList)) {
            this.affectContent = "";
        } else {
            this.affectContent = JSON.toJSONString(entryList);
        }
    }

    public List<LinkedDisplayRuleEntry> getAffectField() {
        if (!StringUtils.hasText(affectContent)) {
            return List.of();
        }
        return JSON.parseArray(affectContent, LinkedDisplayRuleEntry.class);
    }
}
