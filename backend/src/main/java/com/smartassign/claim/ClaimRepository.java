package com.smartassign.claim;

import com.smartassign.domain.ClaimStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimNumberIgnoreCase(String claimNumber);

    boolean existsByClaimNumberIgnoreCase(String claimNumber);

    long countByStatus(ClaimStatus status);

    org.springframework.data.domain.Page<Claim> findByCustomerId(Long customerId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Claim> findByAssignedHandlerIdAndStatusIn(Long handlerId, List<ClaimStatus> statuses,
                                                                                  org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(@Param("id") Long id);

    @Query("select c.id from Claim c where c.status in :statuses order by c.id")
    List<Long> findIdsByStatusIn(@Param("statuses") List<ClaimStatus> statuses);

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumber();
}
