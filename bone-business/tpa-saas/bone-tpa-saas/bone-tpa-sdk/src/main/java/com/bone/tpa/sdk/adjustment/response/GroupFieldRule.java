package com.bone.tpa.sdk.adjustment.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupFieldRule {

    private List<String> fieldNameList;

    private Boolean unique;

    private List<String> fieldNameListA;

    private List<String> fieldNameListB;
}
