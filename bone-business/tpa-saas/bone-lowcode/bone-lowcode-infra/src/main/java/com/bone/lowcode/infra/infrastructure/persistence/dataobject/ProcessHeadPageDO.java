package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
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
@TableName("process_head_page")
public class ProcessHeadPageDO implements Serializable {

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
     * 页面描述
     */
    private String description;

    /**
     * 页面code
     */
    private String code;

    /**
     * 选项集id列表
     */
    private String optionSetId;

    /**
     * 规则项
     */
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String param;

    /**
     * 所属流程类型,1:签收,2:初审,3:录入,4:质检,5:审核,6:复核
     */
    private Byte processType;

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

    public List<Long> getOptionSetIds() {
        List<Long> list = new ArrayList<>();
        if (StringUtils.hasText(optionSetId)) {
            list = Arrays.stream(optionSetId.split(",")).map(Long::parseLong).toList();
        }
        return list;
    }

    public void setOptionSetIds(List<Long> list) {
        if (CollectionUtils.isEmpty(list)) {
            this.optionSetId = "";
        } else {
            this.optionSetId = list.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }
}
