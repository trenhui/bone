package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("file_upload_record")
public class FileUploadRecord {

    private Long id ;

    private String bizType ;

    /**
     * 0 保存
     * 1 删除
     */
    private  Integer  operatorType;
    /**
     * 原来的文件
     */
    private String fileName;
    /**
     * 原来文件的oss 地址
     */
    private String originFileOss ;
    /**
     * 导出要求数据格式
     */
    private String data ;
    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 0 未开始
     * 1 校验中
     * 2 更新中
     * 10 完成
     * 11 失败
     */
    private Integer status;
    /**
     * oss地址
     */
    private String errorFileOss;
    /**
     * 完成百分比
     */
    private BigDecimal percent;


    /**
     * 是否删除，0：正常，1：已删除
     */
    private Integer deleted;

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
}
