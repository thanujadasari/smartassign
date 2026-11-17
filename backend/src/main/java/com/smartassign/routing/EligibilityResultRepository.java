package com.smartassign.routing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EligibilityResultRepository extends JpaRepository<EligibilityResultEntity, Long> {

    List<EligibilityResultEntity> findByRunId(Long runId);

    Optional<EligibilityResultEntity> findByRunIdAndHandlerId(Long runId, Long handlerId);
}
