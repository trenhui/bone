package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("cfg_event_trigger")
public class CfgEventTriggerDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * pageID
     */
    private Long pageId;

    /**
     * 显示名
     */
    private String label;

    /**
     * 样式，0：default，1：info，2：primary，3：success，4：warning，5：danger
     */
    private Byte style;

    /**
     * 事件ID
     */
    private Long eventId;

    /**
     * 展示形式，0：Button，1：Link
     */
    private Byte displayType;

    /**
     * 归属，0：form, 1:block, 2:table行, 3:table左表头, 4:table右表头
     */
    private Byte owner;


    private Long ownerId;

    /**
     * 是否删除，0：正常，1：已删除
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
     * 修改人
     */
    private String updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;
}
