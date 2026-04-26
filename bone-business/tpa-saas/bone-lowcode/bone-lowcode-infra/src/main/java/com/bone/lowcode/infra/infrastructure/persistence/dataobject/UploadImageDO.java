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


@TableName("cfg_upload_image")
@Data
public class UploadImageDO implements Serializable {

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
     * 影像压缩包格式描述
     */
    private String packageDescription;

    /**
     * 文件大小上限
     */
    private Integer fileMaxSize;

    /**
     * 包文件夹上限
     */
    private Integer folderMaxSize;

    /**
     * 限定文件格式
     */
    private String fileFormat;

    /**
     * 支持导入方式,1:新增,2:替换
     */
    private String importType;

    /**
     * 同名文件处理方式,1:新旧都保留,2：覆盖原来的,仅保留新的
     */
    private Byte sameFileHandle;

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

    public List<Byte> getImportTypes() {
        List<Byte> list = new ArrayList<>();
        if (StringUtils.hasText(importType)) {
            list = Arrays.stream(importType.split(",")).map(Byte::parseByte).toList();
        }
        return list;
    }

    public void setImportTypes(List<Byte> list) {
        if (CollectionUtils.isEmpty(list)) {
            this.importType = "";
        } else {
            this.importType = list.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }
}
