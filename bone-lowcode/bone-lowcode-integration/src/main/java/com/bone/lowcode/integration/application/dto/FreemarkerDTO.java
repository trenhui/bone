package com.bone.lowcode.integration.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class FreemarkerDTO {
    @NotEmpty
    private String template;
    @NotEmpty
    private String bodyParam;
    private List<ParamDTO> headerParamList;
}
