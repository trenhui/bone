package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TpaUserQueryResp {
    private String userId ;
    private String userName ;

    private List<Map<String,Object>> groupList;

}
