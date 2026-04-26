package com.bone.tpa.audit.infrastructure.feign.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SysDictDTO {
    private Integer dictId;

    private String dictType;

    private String dictValue;

    private String dictDisplaytext;

    private Integer dictOrder;

    private String dicItemtype;

    private String dictMemo;
}
