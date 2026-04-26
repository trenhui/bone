package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.domain.model.PageHeadField;
import com.bone.lowcode.infra.domain.model.TabCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@TableName("process_list_page")
public class ProcessListPageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 类型，0：模板，1:专属
     */
    private Byte type;

    /**
     * 主体code
     */
    private String bizIdentityCode;

    /**
     * 列表页名称
     */
    private String name;

    /**
     * 编码
     */
    private String code;

    /**
     * 流程类型,1:签收,2:初审
     */
    private Byte processType;

    /**
     * 页面介绍
     */
    private String description;

    /**
     * 是否开启页头,0:否,1:是
     */
    private Byte enablePageHead;

    /**
     * 页头字段所属的模型id
     */
    private Long pageHeadModelId;

    /**
     * 页头字段信息
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String pageHeadFieldList;

    /**
     * 展示形式,0:仅区块字段,1:仅表格,2:兼具
     */
    private Byte bodyType;

    /**
     * 是否开启tab页,0:否,1:是
     */
    private Byte enableTab;

    /**
     * tab页条件列表
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String tabCondition;

    /**
     * 数据范围,1:全局,2:根据账号,3:根据字段
     */
    private Byte dataRange;

    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "修改时间")
    private Date updateTime;

    public List<PageHeadField> getPageHeadFields() {
        List<PageHeadField> list = new ArrayList<>();
        if (StringUtils.hasText(pageHeadFieldList)) {
            list = JSON.parseArray(pageHeadFieldList, PageHeadField.class);
        }
        return list;
    }

    public void setPageHeadFields(List<PageHeadField> fieldList) {
        if (CollectionUtils.isEmpty(fieldList)) {
            this.pageHeadFieldList = null;
        } else {
            this.pageHeadFieldList = JSON.toJSONString(fieldList);
        }
    }

    public List<TabCondition> getTabConditions() {
        List<TabCondition> list = new ArrayList<>();
        if (StringUtils.hasText(tabCondition)) {
            list = JSON.parseArray(tabCondition, TabCondition.class);
        }
        return list;
    }

    public void setTabConditions(List<TabCondition> tabConditionList) {
        if (CollectionUtils.isEmpty(tabConditionList)) {
            this.tabCondition = null;
        } else {
            this.tabCondition = JSON.toJSONString(tabConditionList);
        }
    }
}
