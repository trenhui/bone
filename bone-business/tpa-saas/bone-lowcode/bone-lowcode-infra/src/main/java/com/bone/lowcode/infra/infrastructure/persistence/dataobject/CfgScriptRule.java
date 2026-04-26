package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("cfg_script_rule")
public class CfgScriptRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属页面id（冗余字段）
     */
    private Long pageId;

    /**
     * 源头规则id
     */
    private Long sourceId;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则类型,1:字段动态,2:表格动态,3:表格报存,4:提交条件
     */
    private Byte type;

    /**
     * 触发时机,1:页面初始化后,2:表单输入框失焦,3:表单选择框值变化,4:表格单行改变,5:表格单行保存校验,6:表格单行保存完成后,7:提交校验
     */
    private Byte triggerTime;

    /**
     * 触发字段对象
     */
    private String field;

    /**
     * 触发表格对象
     */
    private String table;

    /**
     * 脚本内容
     */
    private String scriptContent;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 错误提示文案
     */
    private String errorPrompt;

    /**
     * 启用状态,0:未启用,1:已启用
     */
    private Byte status;

    /**
     * 校验方式,0:强校验,阻止作业流程,1:仅提示,可跳过继续作业
     */
    private Byte verifyType;

    /**
     * 0:未删,1:已删
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
}
