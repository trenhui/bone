package com.bone.tpa.claim.application.request;

import lombok.Data;

import java.util.List;

@Data
public class BaseImportVO {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 唯一编码
     */
    private String code;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:附件,3:影像件,4:图片
     */
    private Byte dataType;

    /**
     * 文件大小上限
     */
    private Integer fileMaxSize;

    /**
     * 文件格式,如:xls,xlsx
     */
    private String fileFormat;


    /**
     * 导入方式,1:同时新增或更新,2:仅新增,3:仅更新
     */
    /**
     * 支持导入方式,1:新增,2:替换
     */
    private List<Byte> importTypeList;
}
