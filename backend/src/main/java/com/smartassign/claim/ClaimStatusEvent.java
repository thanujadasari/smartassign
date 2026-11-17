package com.smartassign.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row per status change of a claim - the timeline the customer, supervisor and handler see. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ClaimStatusEvent() {
    }

    public ClaimStatusEvent(Long claimId, String fromStatus, String toStatus, String note, Long actorUserId,
                            String actorName, String actorRole) {
        this.claimId = claimId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorRole = actorRole;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public Instant getCreatedAt() { return createdAt; }
}
