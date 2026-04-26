package com.bone.lowcode.infra.application.vo.groupData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectDropDataVO {

    public SelectDropDataVO(Byte type, String code, String name) {
        this.type = type;
        this.code = code;
        this.name = name;
    }

    //下拉框数据来源,1:选项集,2:组数据
    private Byte type;

    //唯一标识
    private String code;

    //标题
    private String name;

    //扩展属性及值
    private Map<String, String> extraProperty;
}
