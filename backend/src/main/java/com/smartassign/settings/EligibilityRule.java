package com.smartassign.settings;

import com.smartassign.core.eligibility.RuleConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "eligibility_rules")
public class EligibilityRule {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private boolean locked;
    @Column(name = "param_1") private BigDecimal param1;
    @Column(name = "param_2") private BigDecimal param2;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "updated_by", length = 60) private String updatedBy;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public RuleConfig toConfig() {
        return new RuleConfig(code, name, enabled, locked,
                param1 == null ? null : param1.doubleValue(), param2 == null ? null : param2.doubleValue());
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isLocked() { return locked; }
    public BigDecimal getParam1() { return param1; }
    public void setParam1(BigDecimal param1) { this.param1 = param1; }
    public BigDecimal getParam2() { return param2; }
    public void setParam2(BigDecimal param2) { this.param2 = param2; }
    public int getSortOrder() { return sortOrder; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
