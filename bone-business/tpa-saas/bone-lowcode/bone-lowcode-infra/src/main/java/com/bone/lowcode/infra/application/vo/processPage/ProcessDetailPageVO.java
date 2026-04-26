package com.bone.lowcode.infra.application.vo.processPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDetailPageVO {

    private String pageName;

    private List<Object> uploadComponentList;
}
