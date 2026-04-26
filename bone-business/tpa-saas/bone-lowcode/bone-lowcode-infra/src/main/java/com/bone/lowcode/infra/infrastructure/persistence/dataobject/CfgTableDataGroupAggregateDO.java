package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.application.vo.field.GroupAggregateField;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@TableName("cfg_table_data_group_aggregate")
public class CfgTableDataGroupAggregateDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属表格id
     */
    private Long tableId;

    /**
     * 所属页面id
     */
    private Long pageId;

    /**
     * 源头规则id
     */
    private Long sourceId;

    /**
     * 分组聚合描述
     */
    private String name;

    /**
     * 分组字段信息
     */
    private String groupFieldInfo;

    /**
     * 聚合字段信息
     */
    private String aggregateFieldInfo;

    /**
     * 其他展示字段信息
     */
    private String otherFieldInfo;

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

    public void setGroupField(GroupAggregateField groupField) {
        if (groupField != null) {
            this.groupFieldInfo = JSON.toJSONString(groupField);
        }
    }

    public GroupAggregateField getGroupField() {
        if (!StringUtils.hasText(groupFieldInfo)) {
            return null;
        }
        return JSON.parseObject(groupFieldInfo, GroupAggregateField.class);
    }

    public void setAggregateField(List<GroupAggregateField> groupAggregateFieldList) {
        if (groupAggregateFieldList != null) {
            this.aggregateFieldInfo = JSON.toJSONString(groupAggregateFieldList);
        }
    }

    public List<GroupAggregateField> getAggregateField() {
        if (!StringUtils.hasText(aggregateFieldInfo)) {
            return List.of();
        }
        return JSON.parseArray(aggregateFieldInfo, GroupAggregateField.class);
    }

    public void setOtherField(List<GroupAggregateField> otherFieldList) {
        if (otherFieldList != null) {
            this.otherFieldInfo = JSON.toJSONString(otherFieldList);
        }
    }

    public List<GroupAggregateField> getOtherField() {
        if (!StringUtils.hasText(otherFieldInfo)) {
            return List.of();
        }
        return JSON.parseArray(otherFieldInfo, GroupAggregateField.class);
    }
}
