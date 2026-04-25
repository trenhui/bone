package com.bone.studio.generator.domain.code;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import java.util.List;

@Table("code_template")
public class CodeTemplate extends AggregateRoot<Long> {

    private Long id;
    private String name;
    private String description;
    private String type;
    private String templateContent;
    private String language;
    private String framework;
    private List<String> tags;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public String getTemplateContent() {
        return templateContent;
    }

    public String getLanguage() {
        return language;
    }

    public String getFramework() {
        return framework;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private CodeTemplate() {
    }

    public static CodeTemplate create(long id, String name, String description, String type, 
                                     String templateContent, String language, String framework) {
        if (name == null || name.isEmpty()) {
            throw new DomainException("模板名称不能为空");
        }
        if (templateContent == null || templateContent.isEmpty()) {
            throw new DomainException("模板内容不能为空");
        }
        if (language == null || language.isEmpty()) {
            throw new DomainException("语言不能为空");
        }

        CodeTemplate template = new CodeTemplate();
        template.id = id;
        template.name = name;
        template.description = description;
        template.type = type;
        template.templateContent = templateContent;
        template.language = language;
        template.framework = framework;
        template.active = true;
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();

        return template;
    }

    public void update(String name, String description, String type, 
                      String templateContent, String language, String framework) {
        if (name == null || name.isEmpty()) {
            throw new DomainException("模板名称不能为空");
        }
        if (templateContent == null || templateContent.isEmpty()) {
            throw new DomainException("模板内容不能为空");
        }
        if (language == null || language.isEmpty()) {
            throw new DomainException("语言不能为空");
        }

        this.name = name;
        this.description = description;
        this.type = type;
        this.templateContent = templateContent;
        this.language = language;
        this.framework = framework;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void addTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw new DomainException("标签不能为空");
        }
        if (tags == null) {
            tags = new java.util.ArrayList<>();
        }
        if (!tags.contains(tag)) {
            tags.add(tag);
            this.updatedAt = LocalDateTime.now();
        }
    }

    public void removeTag(String tag) {
        if (tags != null) {
            tags.remove(tag);
            this.updatedAt = LocalDateTime.now();
        }
    }
}