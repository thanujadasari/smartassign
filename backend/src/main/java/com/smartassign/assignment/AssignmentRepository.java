package com.smartassign.assignment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {

    List<Assignment> findByClaimIdOrderByCreatedAtDesc(Long claimId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findByIdForUpdate(@Param("id") Long id);

    /** Executes immediately so a new PENDING row can be inserted without violating the one-pending-per-claim index. */
    @Modifying(flushAutomatically = true)
    @Query("update Assignment a set a.status = :to where a.claimId = :claimId and a.status = :from")
    int updateStatusForClaim(@Param("claimId") Long claimId, @Param("from") Assignment.Status from,
                             @Param("to") Assignment.Status to);

    default int cancelPendingForClaim(Long claimId) {
        return updateStatusForClaim(claimId, Assignment.Status.PENDING, Assignment.Status.CANCELLED);
    }
}
