package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.domain.valueobject.OptionSetNodeEnum;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Data
@TableName("option_set_version")
public class OptionSetVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 选项集id
     */
    private Long optionSetId;

    /**
     * 选项集名称
     */
    private String optionSetName;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 当前版本及之前的版本数量
     */
    private Integer versionCount;

    /**
     * 上一个版本id
     */
    private Long lastId;

    /**
     * 选项集内容
     */
    private String content;

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

    //获取选项集内全部对象
    public List<OptionSet> getOptionSetList() {
        if (!StringUtils.hasText(content)) {
            throw new ServiceException(500, "数据异常,版本内容为空");
        }
        return JSON.parseArray(content, OptionSet.class);
    }

    //获取选项集
    public OptionSet getOptionSet() {
        List<OptionSet> optionSetList = getOptionSetList();
        for (OptionSet i : optionSetList) {
            if (i.getNodeType() == OptionSetNodeEnum.ROOT_NODE.getCode()) {
                return i;
            }
        }
        throw new ServiceException(500, "数据异常,无根节点");
    }

    //获取初层选项值
    public List<OptionSet> getSecondLayer() {
        List<OptionSet> optionSetList = getOptionSetList();
        OptionSet root = null;
        for (OptionSet i : optionSetList) {
            if (i.getNodeType() == OptionSetNodeEnum.ROOT_NODE.getCode()) {
                root = i;
                break;
            }
        }
        if (root == null) throw new ServiceException(500, "数据异常,无根节点");

        OptionSet finalRoot = root;
        return optionSetList.stream().filter(i -> Objects.equals(i.getParentId(), finalRoot.getId())).toList();
    }
}
