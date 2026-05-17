package com.bone.masterdata.domain.quality;

import com.bone.core.domain.AggregateRoot;
import com.bone.masterdata.domain.model.quality.event.DataQualityRuleCreatedEvent;
import com.bone.masterdata.domain.model.quality.vo.RuleName;
import com.bone.masterdata.domain.model.quality.vo.RuleSeverity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("meta_data_quality_rule")
public class DataQualityRule extends AggregateRoot<Long> {
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "entity_id")
    private Long masterDataEntityId;
    private RuleName name;
    private String type;
    private String expression;
    private RuleSeverity severity;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DataQualityRule create(
            Long id,
            Long masterDataEntityId,
            RuleName name,
            String type,
            String expression,
            RuleSeverity severity,
            String description
    ) {
        DataQualityRule rule = new DataQualityRule();
        rule.id = id;
        rule.tenantId = 0L;
        rule.masterDataEntityId = masterDataEntityId;
        rule.name = name;
        rule.type = type;
        rule.expression = expression;
        rule.severity = severity;
        rule.enabled = true;
        rule.createdAt = LocalDateTime.now();
        rule.updatedAt = LocalDateTime.now();
        rule.addDomainEvent(new DataQualityRuleCreatedEvent(rule));
        return rule;
    }

    public void update(
            RuleName name,
            String type,
            String expression,
            RuleSeverity severity,
            String description
    ) {
        this.name = name;
        this.type = type;
        this.expression = expression;
        this.severity = severity;
        this.updatedAt = LocalDateTime.now();
    }

    /** meta_data_quality_rule 无 description 列，API 兼容返回空。 */
    public String getDescription() {
        return null;
    }
}
