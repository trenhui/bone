package com.bone.core.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 失败详情（对齐 RFC 7807 子集），作为 {@link ApiResponse#data} 承载。
 */
@Data
@NoArgsConstructor
public class ProblemDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;
    private String errorCode;
    private String traceId;
    private List<FieldError> errors = new ArrayList<>();

    public static ProblemDetail of(String errorCode, int httpStatus, String detail) {
        ProblemDetail p = new ProblemDetail();
        p.setErrorCode(errorCode);
        p.setStatus(httpStatus);
        p.setDetail(detail);
        p.setTitle(errorCode);
        p.setType("about:blank");
        return p;
    }

    @Data
    @NoArgsConstructor
    public static class FieldError implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String field;
        private String message;
        private Object rejectedValue;
    }
}
