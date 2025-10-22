package com.bone.smartmeta.engine.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 验证结果类
 * 用于存储元数据验证的结果
 */
public class ValidationResult {
    
    private boolean valid;
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
    
    // 构造方法
    private ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    // 静态工厂方法 - 创建成功结果
    public static ValidationResult success() {
        return new ValidationResult();
    }
    
    // builder方法
    public static Builder builder() {
        return new Builder();
    }
    
    // 添加错误
    public void addError(ValidationError error) {
        this.errors.add(error);
        this.valid = false;
    }
    
    // 添加警告
    public void addWarning(ValidationWarning warning) {
        this.warnings.add(warning);
    }
    
    // getter方法
    public boolean isValid() {
        return valid;
    }
    
    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    public List<ValidationWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
    
    // 错误内部类
    public static class ValidationError {
        private String message;
        private String fieldPath;
        private String code;
        
        private ValidationError() {}
        
        public static Builder builder() {
            return new Builder();
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getFieldPath() {
            return fieldPath;
        }
        
        public String getCode() {
            return code;
        }
        
        public static class Builder {
            private ValidationError error = new ValidationError();
            
            public Builder message(String message) {
                error.message = message;
                return this;
            }
            
            public Builder fieldPath(String fieldPath) {
                error.fieldPath = fieldPath;
                return this;
            }
            
            public Builder code(String code) {
                error.code = code;
                return this;
            }
            
            public ValidationError build() {
                return error;
            }
        }
    }
    
    // 警告内部类
    public static class ValidationWarning {
        private String message;
        private String fieldPath;
        private String code;
        
        private ValidationWarning() {}
        
        public static Builder builder() {
            return new Builder();
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getFieldPath() {
            return fieldPath;
        }
        
        public String getCode() {
            return code;
        }
        
        public static class Builder {
            private ValidationWarning warning = new ValidationWarning();
            
            public Builder message(String message) {
                warning.message = message;
                return this;
            }
            
            public Builder fieldPath(String fieldPath) {
                warning.fieldPath = fieldPath;
                return this;
            }
            
            public Builder code(String code) {
                warning.code = code;
                return this;
            }
            
            public ValidationWarning build() {
                return warning;
            }
        }
    }
    
    // Builder内部类
    public static class Builder {
        private ValidationResult result = new ValidationResult();
        
        public Builder error(ValidationError error) {
            result.addError(error);
            return this;
        }
        
        public Builder warning(ValidationWarning warning) {
            result.addWarning(warning);
            return this;
        }
        
        public ValidationResult build() {
            return result;
        }
    }
}