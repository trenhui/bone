package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("cfg_upload_picture")
public class UploadPictureDO implements Serializable {

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
     * 单张大小上限
     */
    private Integer singleMaxSize;

    /**
     * 限定文件格式
     */
    private String fileFormat;

    /**
     * 上传张数限定
     */
    private Integer maxCount;

    /**
     * 导入操作说明
     */
    private String importDescription;

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
}
