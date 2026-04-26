package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Data
@TableName("cfg_table_data_row_verify")
public class CfgTableDataRowVerifyDO implements Serializable {

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
     * 字段id列表
     */
    private String fieldIdList;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * 操作符
     */
    private String operator;

    /**
     * 值类型
     */
    private String valueType;

    /**
     * 固定值文本或动态字段id
     */
    private String value;

    /**
     * 提交时不符规则的错误提示文案
     */
    private String errorPrompt;

    /**
     * 启用状态，0：不启用，1：启用
     */
    private Byte status;

    /**
     * 序号
     */
    private Integer sequence;

    /**
     * 校验方式，0：强校验，阻止作业流程，1：仅提示，可跳过继续作业
     */
    private Byte verifyType;

    /**
     * 0：未删，1：已删
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
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private Date updateTime;

    public List<Long> getFieldIds() {
        List<Long> list = new ArrayList<>();
        if (StringUtils.hasText(fieldIdList)) {
            list = Arrays.stream(fieldIdList.split(",")).map(Long::parseLong).toList();
        }
        return list;
    }

    public void setFieldIds(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            this.fieldIdList = "";
        } else {
            this.fieldIdList = idList.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }
}
