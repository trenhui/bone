package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

import java.util.List;

@Data
public class OptionSetListVO {

    private String code;

    List<OptionSetVO2> voList;
}
