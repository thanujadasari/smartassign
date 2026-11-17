package com.smartassign.routing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRecommendationRepository extends JpaRepository<AssignmentRecommendation, Long> {

    List<AssignmentRecommendation> findByRunIdOrderByRankAsc(Long runId);

    Optional<AssignmentRecommendation> findByRunIdAndRank(Long runId, int rank);

    Optional<AssignmentRecommendation> findByRunIdAndHandlerId(Long runId, Long handlerId);
}
