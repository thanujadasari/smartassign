package com.smartassign.claim;

import com.smartassign.auth.User;
import com.smartassign.domain.ClaimStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the claim timeline in the same transaction as the change it describes. */
@Component
public class ClaimHistory {

    private final ClaimStatusEventRepository repo;

    public ClaimHistory(ClaimStatusEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Claim claim, ClaimStatus from, ClaimStatus to, String note, User actor) {
        repo.save(new ClaimStatusEvent(claim.getId(), from == null ? null : from.name(), to.name(), note,
                actor == null ? null : actor.getId(),
                actor == null ? "System" : actor.getFullName(),
                actor == null ? "SYSTEM" : actor.getRole().name()));
    }
}
