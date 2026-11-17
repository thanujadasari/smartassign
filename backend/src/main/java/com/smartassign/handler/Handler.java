package com.smartassign.handler;

import com.smartassign.core.HandlerSnapshot;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import com.smartassign.domain.RecordSource;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "handlers")
public class Handler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handler_code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** The HANDLER login that owns this profile (null only for profiles imported from ClaimCenter). */
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 160)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_expertise", nullable = false, length = 30)
    private Expertise primaryExpertise;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "handler_skills", joinColumns = @JoinColumn(name = "handler_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "skill_code", length = 30)
    private Set<ClaimType> skills = new HashSet<>();

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Column(name = "current_workload", nullable = false)
    private int currentWorkload;

    @Column(name = "max_workload", nullable = false)
    private int maxWorkload;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "unavailable_reason", columnDefinition = "text")
    private String unavailableReason;

    @Column(name = "availability_changed_at")
    private Instant availabilityChangedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 60)
    private String location;

    /** resolved on time / resolved - computed from claims resolved in this application; null = no history yet */
    @Column(name = "success_rate", precision = 4, scale = 3)
    private BigDecimal successRate;

    /** mean days from assignment to resolution; null = no history yet */
    @Column(name = "avg_resolution_days", precision = 5, scale = 1)
    private BigDecimal avgResolutionDays;

    @Column(name = "resolved_count", nullable = false)
    private int resolvedCount;

    @Column(name = "resolved_on_time_count", nullable = false)
    private int resolvedOnTimeCount;

    @Column(name = "total_resolution_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalResolutionDays = BigDecimal.ZERO;

    @Column(name = "similar_claims_handled", nullable = false)
    private int similarClaimsHandled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordSource source = RecordSource.STAFF_REGISTRATION;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /*
     * Neutral values used for RANKING ONLY while a handler has no resolved claims in this application.
     * They are the medians of the handler population in the training dataset (success rate 0.746,
     * average resolution 9.4 days, 57 similar claims), so a new handler is neither rewarded nor
     * penalised for having no history. They are never shown as the handler's statistics.
     */
    public static final double NEUTRAL_SUCCESS_RATE = 0.746;
    public static final double NEUTRAL_AVG_RESOLUTION_DAYS = 9.4;
    public static final int NEUTRAL_SIMILAR_CLAIMS = 57;

    public boolean hasHistory() {
        return resolvedCount > 0;
    }

    public HandlerSnapshot toSnapshot() {
        Set<ClaimType> s = skills == null || skills.isEmpty() ? primaryExpertise.impliedSkills() : EnumSet.copyOf(skills);
        double sr = hasHistory() && successRate != null ? successRate.doubleValue() : NEUTRAL_SUCCESS_RATE;
        double ard = hasHistory() && avgResolutionDays != null ? avgResolutionDays.doubleValue() : NEUTRAL_AVG_RESOLUTION_DAYS;
        int similar = hasHistory() ? similarClaimsHandled : NEUTRAL_SIMILAR_CLAIMS;
        return new HandlerSnapshot(id, code, fullName, primaryExpertise, s, experienceYears, currentWorkload, maxWorkload,
                available, active, location, sr, ard, similar, hasHistory());
    }

    /** Records one resolution and recomputes the real statistics. */
    public void recordResolution(double days, boolean onTime) {
        resolvedCount++;
        if (onTime) resolvedOnTimeCount++;
        similarClaimsHandled++;
        totalResolutionDays = totalResolutionDays.add(BigDecimal.valueOf(days));
        successRate = BigDecimal.valueOf((double) resolvedOnTimeCount / resolvedCount).setScale(3, java.math.RoundingMode.HALF_UP);
        avgResolutionDays = totalResolutionDays.divide(BigDecimal.valueOf(resolvedCount), 1, java.math.RoundingMode.HALF_UP);
    }

    public double utilization() {
        return maxWorkload <= 0 ? 1.0 : (double) currentWorkload / maxWorkload;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUnavailableReason() { return unavailableReason; }
    public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }
    public Instant getAvailabilityChangedAt() { return availabilityChangedAt; }
    public void setAvailabilityChangedAt(Instant availabilityChangedAt) { this.availabilityChangedAt = availabilityChangedAt; }
    public int getResolvedCount() { return resolvedCount; }
    public int getResolvedOnTimeCount() { return resolvedOnTimeCount; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Expertise getPrimaryExpertise() { return primaryExpertise; }
    public void setPrimaryExpertise(Expertise primaryExpertise) { this.primaryExpertise = primaryExpertise; }
    public Set<ClaimType> getSkills() { return skills; }
    public void setSkills(Set<ClaimType> skills) {
        this.skills.clear();
        if (skills != null) this.skills.addAll(skills);
    }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
    public int getCurrentWorkload() { return currentWorkload; }
    public void setCurrentWorkload(int currentWorkload) { this.currentWorkload = currentWorkload; }
    public int getMaxWorkload() { return maxWorkload; }
    public void setMaxWorkload(int maxWorkload) { this.maxWorkload = maxWorkload; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getSuccessRate() { return successRate; }
    public void setSuccessRate(BigDecimal successRate) { this.successRate = successRate; }
    public BigDecimal getAvgResolutionDays() { return avgResolutionDays; }
    public void setAvgResolutionDays(BigDecimal avgResolutionDays) { this.avgResolutionDays = avgResolutionDays; }
    public int getSimilarClaimsHandled() { return similarClaimsHandled; }
    public void setSimilarClaimsHandled(int similarClaimsHandled) { this.similarClaimsHandled = similarClaimsHandled; }
    public RecordSource getSource() { return source; }
    public void setSource(RecordSource source) { this.source = source; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
