package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.domain.model.PageHeadField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@TableName("process_detail_page")
public class ProcessDetailPageDO implements Serializable {

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
     * 所属流程类型,1:签收,2:初审,3:录入,4:质检,5:审核,6:复核
     */
    private Byte processType;

    /**
     * 编码
     */
    private String code;

    /**
     * 详情页面名称
     */
    private String name;

    /**
     * 页面介绍说明
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
    private String pageHeadFieldList;

    /**
     * 打开详情弹窗,0:否,1:是
     */
    private Byte openDetail;

    /**
     * 初审标准规范
     */
    private String specification;

    /**
     * 清晰提示文案
     */
    private String tip;

    /**
     * 影像清晰码值
     */
    private Byte imageQuality;

    /**
     * 影像分类码值
     */
    private Byte imageType;

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
        this.pageHeadFieldList = JSON.toJSONString(fieldList);
    }
}
