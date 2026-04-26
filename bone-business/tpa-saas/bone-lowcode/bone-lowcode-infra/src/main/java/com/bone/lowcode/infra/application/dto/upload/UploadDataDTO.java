package com.bone.lowcode.infra.application.dto.upload;

import com.bone.lowcode.infra.domain.model.GroupFieldRule;
import com.bone.lowcode.infra.domain.model.SingleFieldRule;
import lombok.Data;

import java.util.List;

@Data
public class UploadDataDTO {

    private Long id;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:附件,3:影像件,4:图片
     */
    private Byte dataType;

    /**
     * 导入字段id列表
     */
    private List<Long> fieldIdList;

    /**
     * 单个字段规则
     */
    private List<SingleFieldRule> singleFieldRuleList;

    /**
     * 组合字段规则
     */
    private List<GroupFieldRule> groupFieldRuleList;

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
     * 文件格式,1:xls,2:xlsx
     */
    private Byte fileFormat;

    /**
     * 导入方式,1:同时新增或更新,2:仅新增,3:仅更新
     */
    private List<Byte> importTypeList;

    /**
     * 导入校验方式,1:所有数据校验后导入,2:逐行导入
     */
    private Byte checkType;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
