package com.smartassign.core.ml;

import com.smartassign.core.ClaimSnapshot;
import com.smartassign.core.HandlerSnapshot;

import java.util.List;

/** Abstraction over the predicted-fit (ML) service so the core engine stays framework-free and testable. */
public interface SuitabilityScorer {

    /**
     * Scores ELIGIBLE candidates only. Implementations must never throw: on any failure they return
     * {@link SuitabilityScores#unavailable(String)}.
     */
    SuitabilityScores score(ClaimSnapshot claim, List<HandlerSnapshot> eligibleCandidates);

    SuitabilityScorer DISABLED = (claim, candidates) -> SuitabilityScores.unavailable("disabled");
}
