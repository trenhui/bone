package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.domain.model.GroupFieldRule;
import com.bone.lowcode.infra.domain.model.SingleFieldRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@TableName("cfg_upload_data")
@Data
public class UploadDataDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 唯一编码
     */
    private String code;

    /**
     * 类型，0：模板，1:专属
     */
    private Byte type;

    /**
     * 业务身份编码
     */
    private String bizIdentityCode;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:影像件,3:图片,4:附件
     */
    private Byte dataType;

    /**
     * 适用数据模型id列表
     */
    private Long modelId;

    /**
     * 导入字段id列表
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String fieldIdList;

    /**
     * 单个字段规则
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String singleFieldRule;

    /**
     * 组合字段规则
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String groupFieldRule;

    /**
     * 模板文件名
     */
    private String templateFileName;

    /**
     * 文件大小上限
     */
    private Integer fileMaxSize;

    /**
     * 文件数据上限
     */
    private Integer fileMaxCount;

    /**
     * 文件格式,如:xls,xlsx
     */
    private String fileFormat;

    /**
     * 文件表头校验方式，1:按照模板表头名称一一对应
     */
    private Byte headerCheckMode;

    /**
     * 导入方式,1:同时新增或更新,2:仅新增,3:仅更新
     */
    private String importTypeList;

    /**
     * 导入校验方式,1:所有数据校验后导入,2:逐行导入
     */
    private Byte checkType;

    /**
     * 导入操作说明
     */
    private String importDescription;

    /**
     * 是否删除，0：未删除，1：已删除
     */
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "修改时间")
    private Date updateTime;

    public List<Long> getFieldIds() {
        List<Long> list = new ArrayList<>();
        if (StringUtils.hasText(fieldIdList)) {
            list = Arrays.stream(fieldIdList.split(",")).map(Long::parseLong).toList();
        }
        return list;
    }

    public void setFieldIds(List<Long> fieldIdList) {
        String fieldIdListStr =
                fieldIdList.stream().map(String::valueOf).collect(Collectors.joining(","));
        this.fieldIdList = fieldIdListStr;
    }

    public List<SingleFieldRule> getSingleFieldRules() {
        List<SingleFieldRule> list = new ArrayList<>();
        if (StringUtils.hasText(singleFieldRule)) {
            list = JSON.parseArray(singleFieldRule, SingleFieldRule.class);
        }
        return list;
    }

    public List<GroupFieldRule> getGroupFieldRules() {
        List<GroupFieldRule> list = new ArrayList<>();
        if (StringUtils.hasText(groupFieldRule)) {
            list = JSON.parseArray(groupFieldRule, GroupFieldRule.class);
        }
        return list;
    }

    public List<Byte> getImportTypes() {
        List<Byte> list = new ArrayList<>();
        if (StringUtils.hasText(importTypeList)) {
            list = JSON.parseArray(importTypeList, Byte.class);
        }
        return list;
    }
}
