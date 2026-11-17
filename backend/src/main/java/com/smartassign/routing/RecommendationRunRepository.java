package com.smartassign.routing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, Long> {

    Optional<RecommendationRun> findFirstByClaimIdOrderByCreatedAtDescIdDesc(Long claimId);

    Optional<RecommendationRun> findFirstByClaimIdAndRunTypeOrderByCreatedAtDescIdDesc(Long claimId, RecommendationRun.RunType runType);

    List<RecommendationRun> findTop20ByClaimIdOrderByCreatedAtDesc(Long claimId);
}
