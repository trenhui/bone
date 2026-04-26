package com.bone.tpa.sdk.adjustment.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class SingleFieldRule {

    private String bizName;

    private Boolean required;

    private Boolean unique;

}
